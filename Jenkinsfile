pipeline {
    agent none

    environment {
        APP_NAME = "npc-kura"
        DOCKER_HUB_USER = "rahul54726"
        DOCKER_IMAGE = "${DOCKER_HUB_USER}/npc-kura-app:latest"
        EC2_IP = "65.2.149.188"
        EC2_USER = "ubuntu"
        DOCKER_SOCKET = '/var/run/docker.sock'
    }

    stages {
        stage('Checkout Source Code') {
            agent any
            steps {
                echo 'Pulling latest code from the Git branch...'
                checkout scm
            }
        }

        stage('Build Java Application') {
            agent any
            steps {
                echo 'Building Spring Boot JAR using Maven Wrapper...'
                sh 'chmod +x mvnw'
                sh './mvnw clean package -DskipTests'
            }
        }

        stage('Build Docker Image') {
            agent {
                docker {
                    image 'docker:24-cli'
                    reuseNode true
                    args "-v ${DOCKER_SOCKET}:/var/run/docker.sock"
                }
            }
            steps {
                echo 'Building Docker Image from Dockerfile...'
                sh 'docker build -t ${DOCKER_IMAGE} .'
            }
        }

        stage('Push to Docker Hub') {
            agent {
                docker {
                    image 'docker:24-cli'
                    reuseNode true
                    args "-v ${DOCKER_SOCKET}:/var/run/docker.sock"
                }
            }
            steps {
                echo 'Logging into Docker Hub and pushing the image...'
                withCredentials([usernamePassword(credentialsId: 'dockerhub-creds', passwordVariable: 'DOCKER_PASS', usernameVariable: 'DOCKER_USER')]) {
                    sh 'echo $DOCKER_PASS | docker login -u $DOCKER_USER --password-stdin'
                    sh 'docker push ${DOCKER_IMAGE}'
                }
            }
        }

        stage('Deploy to AWS EC2') {
            agent any
            steps {
                echo 'Connecting to EC2 via SSH and deploying the new container...'
                withCredentials([usernamePassword(credentialsId: 'dockerhub-creds', passwordVariable: 'DOCKER_PASS', usernameVariable: 'DOCKER_USER')]) {
                    sshagent(['ec2-ssh-key']) {
                        sh """
                        ssh -o StrictHostKeyChecking=no ${EC2_USER}@${EC2_IP} "
                            echo '${DOCKER_PASS}' | sudo docker login -u '${DOCKER_USER}' --password-stdin &&
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
            node('') {
                cleanWs()
            }
        }
    }
}
