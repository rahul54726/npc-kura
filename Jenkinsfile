pipeline{
    agent any
    environment{
    APP_NAME = "npc-kura"
    DOCKER_IMAGE = "npc-kura-app:${env.BUILD_NUMBER}"

    }
    stages{
        stage('Checkout Source Code'){
            steps{
                echo 'Pulling latest code from Git Branch...'
                checkout scm
            }
        }
        stage('Build Java Application'){
            steps{
                echo 'Building Spring Boot JAR using Maven Wrapper...'
                sh 'chmod +x mvnw'
                sh './mvnw clean package -DskipTests'
            }
        }
        stage('Test Docker Build') {
            steps {
                echo 'Building Docker Image from Dockerfile...'
                sh "docker build -t ${DOCKER_IMAGE} ."
                }
            }
    }

    post{
        success{
            echo 'PipeLine executed Successfully ready for deployment'
        }
        failure{
            echo 'PipeLine failed check logs.'
        }
        always{
        echo 'cleaning up workSpace...'
        cleanWs()
        }
    }
}