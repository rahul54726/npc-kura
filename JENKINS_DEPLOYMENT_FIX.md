# Jenkins Deployment Fix

## What was failing

### Error 1 — `docker: not found`

The pipeline failed at **Build Docker Image**:

```text
docker: not found
ERROR: script returned exit code 127
```

Maven built successfully. Jenkins could not run `docker build` because the Jenkins container had **no Docker CLI** and **no access to the host Docker daemon**.

### Error 2 — `Invalid agent type "docker"`

After an earlier fix attempt, the pipeline failed at startup:

```text
Invalid agent type "docker" specified. Must be one of [any, label, none]
```

That happens when the **Docker Pipeline** plugin is not installed. The current `Jenkinsfile` does **not** use `agent { docker { ... } }` — it uses plain `sh 'docker ...'` commands with `agent any`, so no extra plugin is needed for Docker.

## Root cause

Jenkins runs inside a container (`/var/jenkins_home/...`). That container:

1. Did not include the Docker CLI
2. Did not mount the host Docker socket (`/var/run/docker.sock`)

Without both, `docker` commands cannot work.

## What we changed

### 1. `Jenkinsfile` — standard agents only, shell Docker commands

| Change | Why |
|--------|-----|
| `agent any` for the whole pipeline | Works without the Docker Pipeline plugin |
| New **Verify Docker** stage | Fails fast with a clear message if Docker is not set up |
| `withCredentials` on **Deploy to AWS EC2** | Docker Hub credentials were missing in the deploy stage |
| Removed `docker logout` from `post` | Avoided another `docker: not found` on failure cleanup |

### 2. `Dockerfile.jenkins` — custom Jenkins image with Docker CLI

Installs `docker.io` and adds the `jenkins` user to the `docker` group.

### 3. `docker-compose.jenkins.yml` — correct Jenkins runtime

Starts Jenkins with:

- A persistent `jenkins_home` volume
- **Host Docker socket mounted** at `/var/run/docker.sock` (required)

## One-time Jenkins server setup

Do this on the machine that runs Jenkins.

### Required Jenkins plugins

In **Manage Jenkins → Plugins**, install:

- **SSH Agent** (for EC2 deploy)
- **Credentials Binding** (for Docker Hub login)

**Docker Pipeline is NOT required** for the current `Jenkinsfile`.

Restart Jenkins after installing plugins.

### Required Jenkins credentials

In **Manage Jenkins → Credentials**, create:

| ID | Type | Used for |
|----|------|----------|
| `dockerhub-creds` | Username with password | Docker Hub login (push + EC2 pull) |
| `ec2-ssh-key` | SSH Username with private key | SSH to EC2 at `65.2.149.188` |

### Start or restart Jenkins with Docker socket access

This step fixes `docker: not found`. Run it on the **Jenkins host** (SSH into the server where Jenkins runs).

**Option A — docker compose (recommended)**

From the repo root on the Jenkins host:

```bash
git pull origin main
docker compose -f docker-compose.jenkins.yml up -d --build
```

**Option B — plain docker run**

If Jenkins is already running, stop and remove the old container first:

```bash
docker stop jenkins && docker rm jenkins
docker build -f Dockerfile.jenkins -t my-jenkins .
docker run -d \
  --name jenkins \
  -p 8080:8080 -p 50000:50000 \
  -v jenkins_home:/var/jenkins_home \
  -v /var/run/docker.sock:/var/run/docker.sock \
  --group-add $(getent group docker | cut -d: -f3) \
  my-jenkins
```

**Important:**

- The host must have Docker installed and running
- `-v /var/run/docker.sock:/var/run/docker.sock` gives Jenkins access to the Docker daemon
- `Dockerfile.jenkins` installs the `docker` CLI inside the Jenkins container

### Verify Docker from inside Jenkins

In Jenkins, run a **Pipeline script** test or check the **Verify Docker** stage output:

```bash
docker version
docker info
```

Both must succeed before the full deploy pipeline will work.

## Deploy the fix to GitHub

Commit and push from your dev machine:

```bash
git add Jenkinsfile Dockerfile.jenkins docker-compose.jenkins.yml JENKINS_DEPLOYMENT_FIX.md
git commit -m "fix: use shell docker commands without Docker Pipeline plugin"
git push origin main
```

Then trigger **NPC-Kura-Fresh-Deploy** again in Jenkins.

## Expected pipeline flow after the fix

1. **Checkout** — clone `npc-kura` from GitHub
2. **Verify Docker** — confirm `docker version` and `docker info` work
3. **Build Java Application** — `./mvnw clean package -DskipTests`
4. **Build Docker Image** — `docker build -t rahul54726/npc-kura-app:latest .`
5. **Push to Docker Hub** — login + `docker push`
6. **Deploy to AWS EC2** — SSH to EC2, pull image, restart `npc-kura-container` on port `8081`

## If it still fails

| Symptom | Fix |
|---------|-----|
| `Invalid agent type "docker"` | Pull latest `Jenkinsfile` from GitHub (current version does not use docker agent) |
| `docker CLI not found` | Rebuild Jenkins with `Dockerfile.jenkins` (see setup steps above) |
| `Cannot connect to the Docker daemon` | Mount `/var/run/docker.sock` when starting Jenkins; ensure Docker is running on the host |
| `permission denied` on docker.sock | Add `jenkins` user to `docker` group (`Dockerfile.jenkins` does this) or use `--group-add` on `docker run` |
| `credentialsId dockerhub-creds not found` | Create the credential in Jenkins with that exact ID |
| `ec2-ssh-key not found` | Add EC2 SSH private key credential with that exact ID |
| EC2 deploy fails on `docker login` | Confirm EC2 has Docker installed and `ubuntu` can `sudo docker` |
| App starts but errors on DB | `application.yaml` points at `localhost:5432`; EC2 needs PostgreSQL reachable from the container |

## Summary

Two separate issues were fixed:

1. **`docker: not found`** — Jenkins container needs Docker CLI (`Dockerfile.jenkins`) and the host socket mounted (`docker-compose.jenkins.yml`).
2. **`Invalid agent type "docker"`** — Removed dependency on the Docker Pipeline plugin; the pipeline now uses `agent any` and shell `docker` commands.

Push the updated files, rebuild/restart Jenkins with the socket mount, then re-run the job.
