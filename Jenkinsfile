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
    }

    stages {
        stage('God-Mode Auto-Fix') {
            steps {
                echo 'Applying permanent fix: Installing Docker and fixing socket permissions...'
                sh '''
                # 1. Install Docker CLI on the fly if it is missing
                if ! command -v docker &> /dev/null; then
                    echo "Docker CLI not found! Auto-installing..."
                    apt-get update
                    DEBIAN_FRONTEND=noninteractive apt-get install -y docker.io
                else
                    echo "Docker CLI is already perfectly installed."
                fi

                # 2. Force fix the socket permission so it never gets blocked
                echo "Unlocking Docker Socket..."
                chmod 666 /var/run/docker.sock || true

                # 3. Verify it works
                docker --version
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
                sh 'chmod +x mvnw'
                sh './mvnw clean package -DskipTests'
            }
        }

        stage('Build Docker Image') {
            steps {
                echo 'Building Docker Image from Dockerfile...'
                sh "docker build -t ${DOCKER_IMAGE} ."
            }
        }

        stage('Push to Docker Hub') {
            steps {
                echo 'Logging into Docker Hub and pushing the image...'
                withCredentials([usernamePassword(credentialsId: 'dockerhub-creds', passwordVariable: 'DOCKER_PASS', usernameVariable: 'DOCKER_USER')]) {
                    sh "echo \$DOCKER_PASS | docker login -u \$DOCKER_USER --password-stdin"
                    sh "docker push ${DOCKER_IMAGE}"
                }
            }
        }

        stage('Deploy to AWS EC2') {
            steps {
                echo 'Connecting to EC2 via SSH and deploying the new container...'
                sshagent(['ec2-ssh-key']) {
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
            echo ' Pipeline executed successfully. The application is now live on the EC2 instance.'
        }
        failure {
            echo ' Pipeline failed. Please review the Jenkins console logs for debugging details.'
        }
        always {
            echo 'Cleaning up the Jenkins workspace to free up disk space...'
            cleanWs()
            sh 'docker logout || true'
        }
    }
}