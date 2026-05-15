pipeline {
    agent any

    environment {
        // Automatically pulls the 12-digit ID you saved in Jenkins System settings
        AWS_ACCOUNT_ID = "${env.AWS_ACCOUNT_ID}"
        AWS_REGION     = "ap-south-1"

        // --- CHANGE THIS LINE FOR EACH SERVICE ---
        SERVICE_NAME   = "yt-video-service"

        ECR_REPO_URL   = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${SERVICE_NAME}"
    }

    stages {
        stage('Checkout') {
            steps {
                // Pulls the code from your GitHub Repo
                checkout scm
            }
        }

        stage('Maven Build') {
            steps {
                // Using the Maven Wrapper from your project root
                sh "chmod +x mvnw"
                sh "./mvnw clean package -DskipTests"
            }
        }

        stage('Docker Build & Tag') {
            steps {
                script {
                    // Builds the image on the EC2 (Intel/AMD architecture)
                    sh "docker build -t ${SERVICE_NAME} ."

                    // Tags the image for ECR
                    sh "docker tag ${SERVICE_NAME}:latest ${ECR_REPO_URL}:latest"
                }
            }
        }

        stage('Push to ECR') {
            steps {
                script {
                    // Authenticate Jenkins with ECR using the EC2's IAM Role
                    sh "aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

                    // Push the 'latest' image to your repository
                    sh "docker push ${ECR_REPO_URL}:latest"
                }
            }
        }
    }

    post {
        success {
            echo "Successfully pushed ${SERVICE_NAME} to ECR!"
            // Optional: Clean up the image from the EC2 to save disk space
            sh "docker rmi ${SERVICE_NAME}:latest ${ECR_REPO_URL}:latest || true"
        }
        failure {
            echo "Pipeline failed! Check the console output for errors."
        }
    }
}