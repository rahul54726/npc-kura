pipeline {
    agent any

    environment {
        APP_NAME = "npc-kura"
        DOCKER_HUB_USER = "rahul54726"
        DOCKER_IMAGE = "${DOCKER_HUB_USER}/npc-kura-app:latest"
        // Updated to your new EC2 IP
        EC2_IP = "13.201.62.79"
        EC2_USER = "ubuntu"
        REMOTE_DIR = "/home/ubuntu/npc-kura-deploy"
    }

    stages {
        stage('Checkout Source Code') {
            steps {
                echo 'Pulling latest code from the Git repository...'
                checkout scm
            }
        }

        stage('Test & Build Java Application') {
            steps {
                echo 'Executing Mockito unit tests and packaging the Spring Boot application...'
                sh 'chmod +x mvnw'
                // Pipeline will strictly abort here if any JUnit/Mockito test fails
                sh './mvnw clean package'
            }
        }

        stage('Deploy to AWS EC2') {
            steps {
                echo 'Tests passed successfully. Proceeding with deployment to EC2 instance...'
                sshagent(['ec2-ssh-key']) {
                    sh """
                        set -e
                        JAR_FILE=\$(ls target/kura-*.jar | head -1)
                        SSH_OPTS="-o StrictHostKeyChecking=no"

                        # Ensure remote directory exists
                        ssh \$SSH_OPTS ${EC2_USER}@${EC2_IP} "mkdir -p ${REMOTE_DIR}"

                        # Transfer JAR and Dockerfile to EC2
                        cat "\$JAR_FILE" | ssh \$SSH_OPTS ${EC2_USER}@${EC2_IP} "cat > ${REMOTE_DIR}/app.jar"
                        cat Dockerfile.runtime | ssh \$SSH_OPTS ${EC2_USER}@${EC2_IP} "cat > ${REMOTE_DIR}/Dockerfile"

                        # Execute Docker build and run commands on the EC2 instance
                        ssh \$SSH_OPTS ${EC2_USER}@${EC2_IP} "
                            cd ${REMOTE_DIR} &&
                            sudo docker build -t ${DOCKER_IMAGE} . &&
                            sudo docker stop npc-kura-container || true &&
                            sudo docker rm npc-kura-container || true &&

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
                echo 'Deployment successful. Pushing the new image to Docker Hub registry...'
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
            echo 'Pipeline completed successfully. The application has been tested and deployed to production.'
        }
        failure {
            echo 'Pipeline failed during execution. Please review the build logs for specific test failures or deployment errors.'
        }
        always {
            echo 'Cleaning up the Jenkins workspace...'
            cleanWs()
        }
    }
}