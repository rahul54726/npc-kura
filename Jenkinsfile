pipeline {
    agent any

    environment {
        APP_NAME = "npc-kura"
        DOCKER_HUB_USER = "rahul54726"
        DOCKER_IMAGE = "${DOCKER_HUB_USER}/npc-kura-app:latest"
        EC2_IP = "13.201.39.84"
        EC2_USER = "ubuntu"
        REMOTE_DIR = "/home/ubuntu/npc-kura-deploy"
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
                sh 'chmod +x mvnw'
                sh './mvnw clean package -DskipTests'
            }
        }

        stage('Deploy to AWS EC2') {
            steps {
                echo 'Copying JAR to EC2 and building/running the container there (Docker runs on EC2, not Jenkins)...'
                sshagent(['ec2-ssh-key']) {
                    sh """
                        set -e
                        JAR_FILE=\$(ls target/kura-*.jar | head -1)
                        SSH_OPTS="-o StrictHostKeyChecking=no"

                        # Ensure remote directory exists on the EC2 instance
                        ssh \$SSH_OPTS ${EC2_USER}@${EC2_IP} "mkdir -p ${REMOTE_DIR}"

                        # Copy the compiled JAR and Dockerfile.runtime to the EC2 instance
                        cat "\$JAR_FILE" | ssh \$SSH_OPTS ${EC2_USER}@${EC2_IP} "cat > ${REMOTE_DIR}/app.jar"
                        cat Dockerfile.runtime | ssh \$SSH_OPTS ${EC2_USER}@${EC2_IP} "cat > ${REMOTE_DIR}/Dockerfile"

                        # Build the image and run the container on EC2
                        ssh \$SSH_OPTS ${EC2_USER}@${EC2_IP} "
                            cd ${REMOTE_DIR} &&
                            sudo docker build -t ${DOCKER_IMAGE} . &&
                            sudo docker stop npc-kura-container || true &&
                            sudo docker rm npc-kura-container || true &&

                            # Deploy container within the custom network and inject PostgreSQL credentials
                            sudo docker run -d -p 8081:8081 --name npc-kura-container \\
                            --network kura-net \\
                            -e SPRING_DATASOURCE_URL='jdbc:postgresql://kura-postgres:5432/npc_kura?options=-c%20timezone=Asia/Kolkata' \\
                            -e SPRING_DATASOURCE_USERNAME=postgres \\
                            -e SPRING_DATASOURCE_PASSWORD=rahul23246 \\
                            ${DOCKER_IMAGE}
                        "
                    """
                }
            }
        }

        stage('Push to Docker Hub') {
            steps {
                echo 'Pushing image to Docker Hub from EC2...'
                withCredentials([usernamePassword(credentialsId: 'dockerhub-creds', passwordVariable: 'DOCKER_PASS', usernameVariable: 'DOCKER_USER')]) {
                    sshagent(['ec2-ssh-key']) {
                        sh """
                            ssh -o StrictHostKeyChecking=no ${EC2_USER}@${EC2_IP} "
                                echo '${DOCKER_PASS}' | sudo docker login -u '${DOCKER_USER}' --password-stdin &&
                                sudo docker push ${DOCKER_IMAGE}
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
            cleanWs()
        }
    }
}