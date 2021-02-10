pass="***"
containerName="fr-webui-handler"
networkName="custom0"
localPort=3005
containerPort=8005
imageName="fr-webui-handler"
tag="latest"
repo="ta4h1r"
# Tagging to push image to a cloud registry
# $repo = "ta4h1r"
# docker tag $imageName:$tag $repo/$imageName:$tag
# docker push $repo/$imageName:$tag

echo $pass | sudo docker build -t $imageName:$tag .
echo $pass | sudo docker run -itd --name=$containerName --network=$networkName -p $localPort:$containerPort $imageName:$tag
