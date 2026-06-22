pipeline {
    agent any

    environment {
        APP_NAME = "npc-kura"
        // Docker Hub credentials and image configuration
        DOCKER_HUB_USER = "rahul54726"
        DOCKER_IMAGE = "${DOCKER_HUB_USER}/npc-kura-app:latest"

        // AWS EC2 configuration
        EC2_IP = "65.2.149.188"
        EC2_USER = "ubuntu"

        // Custom persistent path for Docker CLI to survive container restarts
        DOCKER_CMD = "/var/jenkins_home/docker-cli/docker"
    }

    stages {
        stage('Initialize Docker CLI (Auto-Heal)') {
            steps {
                echo 'Ensuring Docker CLI is permanently available in the Jenkins persistent volume...'
                sh '''
                if [ ! -f "${DOCKER_CMD}" ]; then
                    echo "Docker CLI not found. Downloading static binary..."
                    mkdir -p /var/jenkins_home/docker-cli
                    curl -fsSLO https://download.docker.com/linux/static/stable/x86_64/docker-26.0.1.tgz
                    tar -xzf docker-26.0.1.tgz
                    mv docker/docker ${DOCKER_CMD}
                    chmod +x ${DOCKER_CMD}
                    rm -rf docker docker-26.0.1.tgz
                else
                    echo "Docker CLI is already installed at ${DOCKER_CMD}."
                fi
                ${DOCKER_CMD} --version
                '''
            }
        }

        stage('Checkout Source Code') {
            steps {
                echo 'Pulling latest code from the Git branch...'
                checkout scm
            }
        }

        stage('Build Java Application') {
            steps {
                echo 'Building Spring Boot JAR using Maven Wrapper...'
                // Ensure the Maven wrapper has execution permissions
                sh 'chmod +x mvnw'
                // Package the application while skipping tests to speed up the build process
                sh './mvnw clean package -DskipTests'
            }
        }

        stage('Build Docker Image') {
            steps {
                echo 'Building Docker Image from Dockerfile...'
                // Use the persistent Docker CLI path
                sh "${DOCKER_CMD} build -t ${DOCKER_IMAGE} ."
            }
        }

        stage('Push to Docker Hub') {
            steps {
                echo 'Logging into Docker Hub and pushing the image...'
                // Authenticate and push using the Jenkins credentials vault securely
                withCredentials([usernamePassword(credentialsId: 'dockerhub-creds', passwordVariable: 'DOCKER_PASS', usernameVariable: 'DOCKER_USER')]) {
                    sh "echo \$DOCKER_PASS | ${DOCKER_CMD} login -u \$DOCKER_USER --password-stdin"
                    sh "${DOCKER_CMD} push ${DOCKER_IMAGE}"
                }
            }
        }

        stage('Deploy to AWS EC2') {
            steps {
                echo 'Connecting to EC2 via SSH and deploying the new container...'
                // Securely connect to the EC2 instance using the SSH Agent plugin
                sshagent(['ec2-ssh-key']) {
                    // Execute deployment commands on the remote EC2 instance using elevated privileges (sudo)
                    sh """
                    ssh -o StrictHostKeyChecking=no ${EC2_USER}@${EC2_IP} "
                        sudo docker login -u \$DOCKER_USER -p \$DOCKER_PASS &&
                        sudo docker pull ${DOCKER_IMAGE} &&
                        sudo docker stop npc-kura-container || true &&
                        sudo docker rm npc-kura-container || true &&
                        sudo docker run -d -p 8081:8081 --name npc-kura-container ${DOCKER_IMAGE}
                    "
                    """
                }
            }
        }
    }

    post {
        success {
            echo 'Pipeline executed successfully. The application is now live on the EC2 instance.'
        }
        failure {
            echo 'Pipeline failed. Please review the Jenkins console logs for debugging details.'
        }
        always {
            echo 'Cleaning up the Jenkins workspace to free up disk space...'
            cleanWs()
            // Logout from Docker to ensure credential security on the build node
            sh "${DOCKER_CMD} logout || true"
        }
    }
}