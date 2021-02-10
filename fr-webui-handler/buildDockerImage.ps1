$containerName = "fr-webui-handler"
$networkName = "custom0" 
$localPort = 3005
$containerPort = 8005
$imageName = "fr-webui-handler"
$tag = "latest"
$repo = "ta4h1r"

docker build -t ${imageName}:${tag} .
# docker tag ${imageName}:${tag} ${repo}/${imageName}:${tag}
# docker push ${repo}/${imageName}:${tag}

docker run -itd --name=$containerName --network=$networkName -p ${localPort}:${containerPort} $imageName