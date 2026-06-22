# Jenkins Deployment Fix

## What was failing

### Error 1 — `docker: not found` on Jenkins

```text
docker: not found
ERROR: script returned exit code 127
```

Jenkins runs in a container without Docker installed. Maven builds worked; `docker build` on Jenkins could not.

### Error 2 — `Invalid agent type "docker"`

```text
Invalid agent type "docker" specified. Must be one of [any, label, none]
```

The Docker Pipeline plugin is not installed, so `agent { docker { ... } }` is not allowed.

### Error 3 — Verify Docker still fails

```text
ERROR: docker CLI not found on this Jenkins agent.
```

Rebuilding Jenkins with Docker requires server access many users do not have on the Jenkins host.

## Final solution — build on Jenkins, Docker on EC2

**Jenkins never runs Docker.** The pipeline:

1. Builds the Spring Boot JAR on Jenkins (Maven — already works)
2. Copies the JAR + `Dockerfile.runtime` to EC2 over SSH
3. Runs `docker build` and `docker run` **on EC2** (where Docker is already installed)
4. Optionally pushes the image to Docker Hub **from EC2**

This avoids needing Docker on Jenkins entirely.

## Files changed

| File | Purpose |
|------|---------|
| `Jenkinsfile` | Build JAR on Jenkins; deploy via SSH to EC2 |
| `Dockerfile.runtime` | Lightweight image that only needs the pre-built `app.jar` |
| `Dockerfile.jenkins` | Optional — only if you later want Docker on Jenkins |
| `docker-compose.jenkins.yml` | Optional — only if you later want Docker on Jenkins |

## Jenkins setup (minimal)

### Required plugins

- **SSH Agent**
- **Credentials Binding**

**Docker Pipeline is NOT required.**  
**Docker on the Jenkins server is NOT required.**

### Required credentials

| ID | Type | Used for |
|----|------|----------|
| `ec2-ssh-key` | SSH Username with private key | Copy JAR to EC2 and run Docker commands |
| `dockerhub-creds` | Username with password | Push image to Docker Hub from EC2 (optional stage) |

### EC2 prerequisites

On `65.2.149.188` (ubuntu user):

- Docker installed and working: `sudo docker version`
- SSH key from Jenkins credential can log in as `ubuntu`
- Port `8081` open in the security group

## Deploy the fix

```bash
git add Jenkinsfile Dockerfile.runtime JENKINS_DEPLOYMENT_FIX.md
git commit -m "fix: build JAR on Jenkins, run Docker on EC2"
git push origin main
```

Then re-run **NPC-Kura-Fresh-Deploy** in Jenkins.

## Expected pipeline flow

1. **Checkout Source Code** — clone repo from GitHub
2. **Build Java Application** — `./mvnw clean package -DskipTests`
3. **Deploy to AWS EC2**
   - Copy `target/kura-*.jar` → EC2 as `app.jar`
   - Copy `Dockerfile.runtime` → EC2 as `Dockerfile`
   - `sudo docker build -t rahul54726/npc-kura-app:latest .`
   - Stop/remove old container, start new one on port `8081`
4. **Push to Docker Hub** — login and push from EC2

## If it still fails

| Symptom | Fix |
|---------|-----|
| `ec2-ssh-key not found` | Add SSH private key credential in Jenkins with that exact ID |
| `Permission denied (publickey)` | EC2 key pair must match the `ec2-ssh-key` credential |
| `sudo: docker: command not found` on EC2 | Install Docker on EC2: `sudo apt install docker.io` |
| `docker build` fails on EC2 | SSH to EC2 and run `sudo docker build` manually in `/home/ubuntu/npc-kura-deploy` |
| `credentialsId dockerhub-creds not found` | Create Docker Hub credential, or mark Push stage optional |
| App container exits immediately | Check logs: `sudo docker logs npc-kura-container` |
| App starts but DB errors | `application.yaml` uses `localhost:5432`; PostgreSQL must be reachable from the container on EC2 |

## Optional — Docker on Jenkins (not required)

If you later want Jenkins to build images locally, use `Dockerfile.jenkins` and `docker-compose.jenkins.yml` to rebuild Jenkins with the Docker CLI and mount `/var/run/docker.sock`. The current pipeline does **not** depend on this.

## Summary

| Before | After |
|--------|-------|
| Jenkins runs `docker build` | Jenkins runs `mvnw package` only |
| Fails without Docker on Jenkins | Works with SSH + Docker on EC2 |
| Needed Docker Pipeline plugin | Uses only standard `agent any` |

Push the updated files and re-run the job. No Jenkins server rebuild is needed.
