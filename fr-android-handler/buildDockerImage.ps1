$containerName = "fr-android-handler"
$networkName = "custom0" 
$localPort = 3004
$containerPort = 8004
$imageName = "fr-android-handler"
$tag = "latest"
$repo = "ta4h1r"

# docker tag ${imageName}:${tag} ${repo}/${imageName}:${tag}
# docker push ${repo}/${imageName}:${tag}
# docker build -t ${imageName}:${tag} .

docker run -itd --name=$containerName --network=$networkName -p ${localPort}:${containerPort} $imageName