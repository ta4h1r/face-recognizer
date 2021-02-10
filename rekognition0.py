"""
Rekognition basics with aws client library
"""

import csv
import boto3
from botocore.exceptions import ClientError

with open('credentials.csv', 'r') as inputData:  # Open as read
    next(inputData)  # Skips to the next line
    reader = csv.reader(inputData)
    for line in reader:
        access_key_id = line[2]
        secret_access_key = line[3]

client = boto3.client('rekognition',
                      aws_access_key_id=access_key_id,
                      aws_secret_access_key=secret_access_key)


def detectLabelsInLocalPhoto():
    photo = 'WIN_20200226_19_42_02_Pro.jpg'

    # Convert to base64
    with open(photo, 'rb') as source_image:  # Open as read binary
        source_bytes = source_image.read()

    response = client.detect_labels(Image={
        'Bytes': source_bytes
    },
        MaxLabels=10,
        MinConfidence=50
    )

    print(response)


def detectLabelsInS3BucketPhoto():
    response = client.detect_labels(Image={
        'S3Object': {
            'Bucket': 'taibai-fr-bucket',
            'Name': 'Training data/Taahir/WIN_20200224_20_02_47_Pro (2).jpg'
        }
    },
        MaxLabels=10,
        MinConfidence=50
    )

    print(response)


def create_collection(collection_id):
    # Create a collection
    print('Creating collection:' + collection_id)
    response = client.create_collection(CollectionId=collection_id)
    print('Collection ARN: ' + response['CollectionArn'])
    print('Status code: ' + str(response['StatusCode']))
    print('Done...')


def list_collections():
    max_results = 1

    # Display all the collections
    print('Displaying collections...')
    response = client.list_collections(MaxResults=max_results)
    collection_count = 0
    done = False

    while not done:
        collections = response['CollectionIds']

        for collection in collections:
            print (collection)
            collection_count += 1
        if 'NextToken' in response:
            nextToken = response['NextToken']
            response = client.list_collections(NextToken=nextToken, MaxResults=max_results)

        else:
            done = True

    return collection_count


def describe_collection(collection_id):
    print('Attempting to describe collection: ' + collection_id)

    try:
        response = client.describe_collection(CollectionId=collection_id)
        print("Collection Arn: " + response['CollectionARN'])
        print("Face Count: " + str(response['FaceCount']))
        print("Face Model Version: " + response['FaceModelVersion'])
        print("Timestamp: " + str(response['CreationTimestamp']))

    except ClientError as e:
        if e.response['Error']['Code'] == 'ResourceNotFoundException':
            print ('The collection ' + collection_id + ' was not found ')
        else:
            print ('Error other than Not Found occurred: ' + e.response['Error']['Message'])
    print('Done...')


def delete_collection(collection_id):
    print('Attempting to delete collection ' + collection_id)
    status_code = 0
    try:
        response = client.delete_collection(CollectionId=collection_id)
        status_code = response['StatusCode']

    except ClientError as e:
        if e.response['Error']['Code'] == 'ResourceNotFoundException':
            print ('The collection ' + collection_id + ' was not found ')
        else:
            print ('Error other than Not Found occurred: ' + e.response['Error']['Message'])
        status_code = e.response['ResponseMetadata']['HTTPStatusCode']
    return status_code


def add_faces_to_collection(bucket, photo, collection_id):
    response = client.index_faces(CollectionId=collection_id,
                                  Image={'S3Object': {'Bucket': bucket, 'Name': photo}},
                                  ExternalImageId=photo,
                                  MaxFaces=1,
                                  QualityFilter="AUTO",
                                  DetectionAttributes=['ALL'])

    print ('Results for ' + photo)
    print('Faces indexed:')
    for faceRecord in response['FaceRecords']:
        print('  Face ID: ' + faceRecord['Face']['FaceId'])
        print('  Location: {}'.format(faceRecord['Face']['BoundingBox']))

    print('Faces not indexed:')
    for unindexedFace in response['UnindexedFaces']:
        print(' Location: {}'.format(unindexedFace['FaceDetail']['BoundingBox']))
        print(' Reasons:')
        for reason in unindexedFace['Reasons']:
            print('   ' + reason)
    return len(response['FaceRecords'])


def list_faces_in_collection(collection_id):
    maxResults = 2
    faces_count = 0
    tokens = True

    response = client.list_faces(CollectionId=collection_id,
                                 MaxResults=maxResults)

    print('Faces in collection ' + collection_id)

    while tokens:

        faces = response['Faces']

        for face in faces:
            print (face)
            faces_count += 1
        if 'NextToken' in response:
            nextToken = response['NextToken']
            response = client.list_faces(CollectionId=collection_id,
                                         NextToken=nextToken, MaxResults=maxResults)
        else:
            tokens = False
    return faces_count


def delete_faces_from_collection(collection_id, faces):
    response = client.delete_faces(CollectionId=collection_id,
                                   FaceIds=faces)

    print(str(len(response['DeletedFaces'])) + ' faces deleted:')
    for faceId in response['DeletedFaces']:
        print (faceId)
    return len(response['DeletedFaces'])


def match_faces():
    bucket = 'taibai-fr-bucket'
    collectionId = 'collection0'
    fileName = 'taibai_long_ago.jpg'
    threshold = 80
    maxFaces = 10

    response = client.search_faces_by_image(CollectionId=collectionId,
                                            Image={'S3Object': {'Bucket': bucket, 'Name': fileName}},
                                            FaceMatchThreshold=threshold,
                                            MaxFaces=maxFaces)

    faceMatches = response['FaceMatches']
    print ('Matching faces')
    for match in faceMatches:
        print ('FaceId:' + match['Face']['FaceId'])
        print ('Similarity: ' + "{:.2f}".format(match['Similarity']) + "%")
        print


def main():
    collection_id = 'collection0'

    # create_collection(collection_id);

    collection_count = list_collections()
    print("collections: " + str(collection_count))

    print('')

    describe_collection(collection_id)
    # delete_collection(collection_id);
    # print('')

    # indexed_faces_count = add_faces_to_collection('taibai-fr-bucket', 'taibai2.jpg', collection_id)
    # print("Faces indexed count: " + str(indexed_faces_count))

    print('')

    faces = ["29a0ae60-a330-4ff1-b74d-5012458eefd5", "2c9778d3-de81-4502-8e21-6bc80560b809"]
    faces_count = delete_faces_from_collection(collection_id, faces)
    print("deleted faces count: " + str(faces_count))

    faces_count = list_faces_in_collection(collection_id)
    print("faces count: " + str(faces_count))

    # match_faces()


if __name__ == "__main__":
    main()
