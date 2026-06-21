pipeline {
    agent any

    environment {
        APP_NAME = "npc-kura"
        // Replace with your actual Docker Hub username
        DOCKER_HUB_USER = "rahul54726"
        DOCKER_IMAGE = "${DOCKER_HUB_USER}/npc-kura-app:latest"

        // AWS EC2 configuration
        EC2_IP = "13.233.36.233"
        EC2_USER = "ubuntu"
    }

    stages {
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
                // Package the application while skipping tests to speed up the build
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
                // Authenticate and push using Jenkins credentials vault
                withCredentials([usernamePassword(credentialsId: 'dockerhub-creds', passwordVariable: 'DOCKER_PASS', usernameVariable: 'DOCKER_USER')]) {
                    sh "echo \$DOCKER_PASS | docker login -u \$DOCKER_USER --password-stdin"
                    sh "docker push ${DOCKER_IMAGE}"
                }
            }
        }

        stage('Deploy to AWS EC2') {
            steps {
                echo 'Connecting to EC2 via SSH and deploying the new container...'
                // Securely connect to the EC2 instance using the SSH Agent plugin
                sshagent(['ec2-ssh-key']) {
                    sh """
                    ssh -o StrictHostKeyChecking=no ${EC2_USER}@${EC2_IP} "
                        docker login -u \$DOCKER_USER -p \$DOCKER_PASS &&
                        docker pull ${DOCKER_IMAGE} &&
                        docker stop npc-kura-container || true &&
                        docker rm npc-kura-container || true &&
                        docker run -d -p 8081:8081 --name npc-kura-container ${DOCKER_IMAGE}
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
            // Logout from Docker to ensure credential security
            sh 'docker logout || true'
        }
    }
}