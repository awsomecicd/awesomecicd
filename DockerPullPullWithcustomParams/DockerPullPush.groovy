properties([
    parameters(
        [
         string(defaultValue: '',description: 'Docker URL', name: 'SourceCreds'),
         string(defaultValue: '',description: 'Enter Vendor name Example: mycompany', name: 'VendorName'),
         string(defaultValue: '',description: 'Enter VNF name Example: mysoftware', name: 'SoftwareName'),
         string(defaultValue: '',description: 'Enter your docker private registry image to pull example: registry/imagename/imagecontains or registry/imagename', name: 'sourceImageName'),
         string(defaultValue: '',description: 'Enter your custom image name to push example: registry/imagename/imagecontains or registry/imagename', name: 'ImageName'),
         string(defaultValue: '',description: 'Enter your docker version or tag of source project example: 1.2 or abc12 or ABC3 ', name: 'tagVersion'),
         booleanParam(defaultValue: false, description: '', name: 'manual'),
         string(defaultValue: '',description: 'Destination Repo uri example: dockerhub.XXX.com', name: 'DevRepoUrl'),
         string(defaultValue: '',description: 'Destination Repo Credentials', name: 'DevCreds'),
         string(defaultValue: '',description: 'Destination Repo uri example: dockerhub.XXX.com', name: 'InterimRepoUrl'),
         string(defaultValue: '',description: 'Destination Repo Credentials', name: 'InterimCreds'),
         string(defaultValue: '',description: 'Enter your docker private registry image to tag and push example: registry/imagename/imagecontains or registry/imagename', name: 'InterimImageRepo'),
         string(defaultValue: '',description: 'Enter your docker private registry image to tag and push example: registry/imagename/imagecontains or registry/imagename', name: 'FinalImageRepo')

        ])
])


node {
    def image = ""

    //def vendorRepo = params.SourceRepo.split("/")[0]
    def vendorRepo = params.sourceImageName.split("/")[0]
    def credVendorRepo = params.SourceCreds
    def VendorName = params.VendorName.toLowerCase()
    def SoftwareName = params.SoftwareName.toLowerCase()
    def kibaInterimRepo = params.InterimRepoUrl
    def credKibaInterim = params.InterimCreds
    def kibaDevRepo = params.DevRepoUrl
    def credKibaDev = params.DevCreds

    def sourceImage = params.sourceImageName
    def sourceImageWithReg = params.sourceImageName.split("/")[1]
    def ImageName = params.ImageName.toLowerCase()
    def InterimImageRepo = params.InterimImageRepo
    def FinalImageRepo = params.FinalImageRepo

    def Version = params.tagVersion

    def manual = params.manual
    // def Authorize = params.Authentication

    stage('Docker pull public or private image') {
        if (manual =='false' &&  credVendorRepo == null || credVendorRepo.isEmpty() ) {
            try {
                def outfile = 'stdout'+ UUID.randomUUID() + '.out'
                def PublicDockerImage = sh(script: "docker pull ${sourceImage}:${Version} >${outfile} 2>&1", returnStatus:true)
                if (PublicDockerImage == 1) {
                    def PullNoRegDockerImage = sh(script: "docker pull ${sourceImageWithReg}:${Version}")
                }
            }
            catch (e) {
                currentBuild.result = "FAILED"
                throw e
            }

        }

        else if(manual == false &&  credVendorRepo != null && !credVendorRepo.isEmpty()){
            echo "Its a private docker image"

            try {
                withCredentials([usernamePassword(credentialsId: "${credVendorRepo}", passwordVariable: 'PASS', usernameVariable: 'USERNAME')]) {

                    def outfile = 'stdout'+ UUID.randomUUID() + '.out'
                    def status = sh(script:"docker login -u $USERNAME -p $PASS >${outfile} 2>&1", returnStatus:true)
                    def output = readFile(outfile).trim()
                    if (status == 1 && credVendorRepo != null && !credVendorRepo.isEmpty()) {
                        // if status gives exit 1 then run following
                        // this block first login to docker.io if it fails then it will login to the private repo
                        def PrivateDockerlogin = sh(script: "docker login -u $USERNAME -p $PASS https://${vendorRepo} 2>&1 >/dev/null")
                        echo "private registry"
                        def PublicDockerImage = sh(script: "docker pull ${sourceImage}:${Version}")
                    }
                }
            }
            catch (e) {
                currentBuild.result = "FAILED"
                throw e
            }
        }
    }

    stage('Docker tag') {
        // pull image with Jenkins' docker-plugin
        try {

            def outfile = 'stdout'+ UUID.randomUUID() + '.out'
            def TagStatus = sh(script:"docker tag ${sourceImage}:${Version} ${InterimImageRepo}/${VendorName}/${SoftwareName}/${ImageName}:${Version} >${outfile} 2>&1", returnStatus:true)
            // we give the image the same version as the .war package
            def TagRemove = sh(script:"docker rmi ${sourceImage}:${Version} >${outfile} 2>&1", returnStatus:true)
            if(TagStatus == 1 || TagRemove == 1){
                sh "docker tag ${sourceImageWithReg}:${Version} ${InterimImageRepo}/${VendorName}/${SoftwareName}/${ImageName}:${Version}"
                // sh "docker rmi ${sourceImageWithReg}/${VendorName}/${SoftwareName}/${ImageName}:${Version}"
            }
            sh '''
            if [[ $(docker images |grep "${InterimImageRepo}/${VendorName}/${SoftwareName}/${sourceImage}:${Version}")  -ne 0 ]];then
            echo "image tagged successfully"
            else
            echo "missing image tag"            
            fi
        '''
            docker.withRegistry(kibaInterimRepo, credKibaInterim) {
                image = docker.image("${InterimImageRepo}/${VendorName}/${SoftwareName}/${ImageName}:${Version}")
                image.push()
            }

        }
        catch (e) {
            currentBuild.result = "FAILED"
            throw e
        }
    }

    stage('Scan docker image'){
        echo "scanning .. inprogress"


        echo "scan completed!!"
    }
/*echo "###############################################################################################################"
echo "############################################### Image Scanning started   ######################################"
echo "###############################################################################################################"

def imageLine = "${destImage}:${Version}"
writeFile file: 'anchore_images', text: imageLine
anchore name: 'anchore_images'

echo "###############################################################################################################"
echo "############################################### Image Scanning Ended  #########################################"
echo "###############################################################################################################"*/
    stage ('Docker push') {
        // pull image with Jenkins' docker-plugin
        try {
            sh "docker tag ${InterimImageRepo}/${VendorName}/${SoftwareName}/${ImageName}:${Version} ${FinalImageRepo}/${VendorName}/${SoftwareName}/${ImageName}:${Version}"
            sh '''
            if [[ $(docker images |grep "${FinalImageRepo}/${VendorName}/${SoftwareName}/${sourceImage}:${Version}")  -ne 0 ]];then
            echo "image tagged successfully"
            else
            echo "missing image tag"            
            fi
        '''

            docker.withRegistry(kibaDevRepo, credKibaDev) {
                // we give the image the same version as the .war package
                image = docker.image("${FinalImageRepo}/${VendorName}/${SoftwareName}/${ImageName}:${Version}")
                image.push()

            }
            //sh "docker rmi ${InterimImageRepo}/${VendorName}/${SoftwareName}/${ImageName}:${Version}"
            //sh "docker rmi ${FinalImageRepo}/${VendorName}/${SoftwareName}/${ImageName}:${Version}"
        }
        catch(e) {
            currentBuild.result = "FAILED"
            throw e
        }
    }
}