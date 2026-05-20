pipeline {
    agent any

    environment {
        AWS_ACCOUNT_ID = "${env.AWS_ACCOUNT_ID}"
        AWS_REGION     = "ap-south-1"

        // --- SERVICE CONFIGURATION ---
        SERVICE_NAME   = "yt-video-service"
        MANIFEST_FILE  = "apps/video-service.yaml"

        ECR_REPO_URL   = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${SERVICE_NAME}"

        // Dynamic unique tag (e.g., v1, v2, v3) based on the Jenkins build number
        IMAGE_TAG      = "v${env.BUILD_NUMBER}"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Maven Build') {
            steps {
                sh "chmod +x mvnw"
                sh "./mvnw clean package -DskipTests"
            }
        }

        stage('Docker Build & Tag') {
            steps {
                script {
                    // Build and tag locally with the unique ECR URL and build version right away
                    sh "docker build -t ${ECR_REPO_URL}:${IMAGE_TAG} ."
                }
            }
        }

        stage('Push to ECR') {
            steps {
                script {
                    // Authenticate with ECR using the instance profile/IAM role
                    sh "aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

                    // Push ONLY the unique, clean version tag
                    sh "docker push ${ECR_REPO_URL}:${IMAGE_TAG}"
                }
            }
        }

        stage('Update Git Manifest') {
            steps {
                script {
                    // Replace 'github-token' with the exact ID of your Jenkins Personal Access Token credential
                    withCredentials([string(credentialsId: 'github-token', variable: 'GH_TOKEN')]) {

                        sh "rm -rf yt-clone-gitops-manifests"
                        sh "git clone https://${GH_TOKEN}@github.com/porushyadav/yt-clone-gitops-manifests.git"

                        dir('yt-clone-gitops-manifests') {
                            // Find 'yt-video-service:any-tag' and update it to 'yt-video-service:v[BuildNumber]'
                            sh "sed -i 's|${SERVICE_NAME}:.*|${SERVICE_NAME}:${IMAGE_TAG}|g' ${MANIFEST_FILE}"

                            sh """
                                git config user.name "Jenkins-CI"
                                git config user.email "jenkins@mountblue.com"
                                git add ${MANIFEST_FILE}
                                git commit -m "chore: automated image tag update for ${SERVICE_NAME} to ${IMAGE_TAG} [skip ci]"
                                git push origin main
                            """
                        }
                    }
                }
            }
        }
    }

    post {
        success {
            echo "Successfully pushed ${SERVICE_NAME}:${IMAGE_TAG} to ECR and updated Git GitOps Manifest!"
            // Clean local worker images to save disk space
            sh "docker rmi ${ECR_REPO_URL}:${IMAGE_TAG} || true"
        }
        failure {
            echo "Pipeline failed! Check the console output for errors."
        }
    }
}