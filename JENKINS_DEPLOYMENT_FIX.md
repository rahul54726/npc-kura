# Jenkins Deployment Fix

## What was failing

The pipeline failed at **Build Docker Image** with:

```text
docker: not found
ERROR: script returned exit code 127
```

Maven (`./mvnw clean package`) succeeded. Jenkins could not run `docker build` because the Jenkins agent had **no Docker CLI** and **no access to a Docker daemon**.

The `post` block also ran `docker logout`, which failed for the same reason.

## Root cause

Jenkins runs inside a container (`/var/jenkins_home/...`). That container did not include Docker, and the host Docker socket was not mounted. Without both, `docker` commands cannot work.

## What we changed

### 1. `Jenkinsfile` — run Docker steps in a Docker-capable agent

| Change | Why |
|--------|-----|
| Docker build/push stages use `agent { docker { image 'docker:24-cli' ... } }` | Provides the Docker CLI without installing packages during the pipeline |
| Mount `/var/run/docker.sock` into that agent | Lets the CLI talk to the host Docker daemon |
| `reuseNode true` | Reuses the same workspace so the built JAR and source are available |
| `withCredentials` on **Deploy to AWS EC2** | Docker Hub username/password were only available in the push stage before; EC2 deploy could not log in to pull the image |
| Removed `docker logout` from `post` | Avoids another `docker: not found` failure on the main agent |
| `agent none` at pipeline level | Allows different stages to use different agents |

### 2. `Dockerfile.jenkins` — optional custom Jenkins image

Installs `docker.io` and adds the `jenkins` user to the `docker` group so Docker works on the main agent if you prefer not to use the `docker:24-cli` sidecar pattern.

### 3. `docker-compose.jenkins.yml` — correct Jenkins runtime

Starts Jenkins with:

- A persistent `jenkins_home` volume
- **Host Docker socket mounted** at `/var/run/docker.sock` (required)

## One-time Jenkins server setup

Do this on the machine that runs Jenkins (your EC2 Jenkins host or local Docker host).

### Required Jenkins plugins

In **Manage Jenkins → Plugins**, install:

- **Docker Pipeline**
- **SSH Agent**
- **Credentials Binding**

Restart Jenkins after installing.

### Required Jenkins credentials

In **Manage Jenkins → Credentials**, create:

| ID | Type | Used for |
|----|------|----------|
| `dockerhub-creds` | Username with password | Docker Hub login (push + EC2 pull) |
| `ec2-ssh-key` | SSH Username with private key | SSH to EC2 at `65.2.149.188` |

### Start or restart Jenkins with Docker socket access

**Option A — docker compose (recommended)**

From the repo root on the Jenkins host:

```bash
docker compose -f docker-compose.jenkins.yml up -d --build
```

**Option B — plain docker run**

If Jenkins is already running, stop it first, then:

```bash
docker build -f Dockerfile.jenkins -t my-jenkins .
docker run -d \
  --name jenkins \
  -p 8080:8080 -p 50000:50000 \
  -v jenkins_home:/var/jenkins_home \
  -v /var/run/docker.sock:/var/run/docker.sock \
  my-jenkins
```

**Important:** The host must have Docker installed. The `-v /var/run/docker.sock:/var/run/docker.sock` line is what fixes `docker: not found` / daemon access.

### Verify Docker from inside Jenkins

Open **Manage Jenkins → Script Console** or run a test pipeline stage:

```bash
docker version
docker info
```

Both should succeed before you rely on the full deploy pipeline.

## Deploy the fix to GitHub

Commit and push the updated files so Jenkins picks up the new `Jenkinsfile`:

```bash
git add Jenkinsfile Dockerfile.jenkins docker-compose.jenkins.yml JENKINS_DEPLOYMENT_FIX.md
git commit -m "fix: enable Docker in Jenkins pipeline for automated deployment"
git push origin main
```

Then trigger **NPC-Kura-Fresh-Deploy** again in Jenkins.

## Expected pipeline flow after the fix

1. **Checkout** — clone `npc-kura` from GitHub
2. **Build Java Application** — `./mvnw clean package -DskipTests` → `target/kura-0.0.1-SNAPSHOT.jar`
3. **Build Docker Image** — `docker build -t rahul54726/npc-kura-app:latest .`
4. **Push to Docker Hub** — login + `docker push`
5. **Deploy to AWS EC2** — SSH to EC2, pull image, restart `npc-kura-container` on port `8081`

## If it still fails

| Symptom | Fix |
|---------|-----|
| `Cannot connect to the Docker daemon` | Mount `/var/run/docker.sock` when starting Jenkins; ensure Docker is running on the host |
| `docker:24-cli` pull errors | Jenkins agent needs outbound internet, or pre-pull `docker pull docker:24-cli` on the host |
| `credentialsId dockerhub-creds not found` | Create the credential in Jenkins with that exact ID |
| `ec2-ssh-key not found` | Add EC2 SSH private key credential with that exact ID |
| EC2 deploy fails on `docker login` | Confirm EC2 has Docker installed and the `ubuntu` user can `sudo docker` |
| App starts but errors on DB | `application.yaml` points at `localhost:5432`; EC2 needs PostgreSQL reachable from the container (separate infra task) |

## Summary

The failure was **not** a Maven or Spring Boot problem. Jenkins needed **Docker CLI access** and **Docker socket access** on the server. The updated `Jenkinsfile` runs Docker commands in a `docker:24-cli` agent with the socket mounted, and `docker-compose.jenkins.yml` documents how to run Jenkins with the socket attached permanently.
