// Dependencies
var AWS = require('aws-sdk');
Table = require('./inputModel');

// AWS credentials
AWS.config.update({
  region: "us-east-1",
  credentials: new AWS.CognitoIdentityCredentials({
    IdentityPoolId: "<identity-pool-id-with-rekognition-and-s3-roles>"
  })
});

//Definitions
var s3 = new AWS.S3({
  region: 'us-east-1',
  s3ForcePathStyle: true
});
var bucket = 'fr-module-faces';
var rekognition = new AWS.Rekognition({
  apiVersion: '2016-06-27'
});

// VIEW ALL
exports.index = (req, res) => {
	console.log('Getting all table data');
	Table.get((err, documents) => {
		if (err) {
			res.json({
				status: "error",
				message: err
			});
		}
		res.json({
			status: "OK",
			message: "Documents retrieved successfully",
			data: documents
		});
			console.log("Query done");
	});
};


exports.handler = async function (req, res) {

	try {
		const event = req.body;
		// console.log(event);

		switch (event.command) {
			case "List":
				console.log('handler: List Items...');
				var obj = {};
				var names = [];
				var roles = [];
				var ids = [];
				await listItems(obj, names, ids, roles, res);
				console.log("handler: List Items: Done");
				break;
			case "Update":
				/**
				 * Updates the selected entity in the face-index-table and in the S3 bucket
				 */
				 // Applying changes to DB
				if (event.Role == "") {
					event.Role = "N/A";
				}
				console.log("handler: Update DB...");
				await Table.updateOne({id: event.id}, {Role: event.Role, Name: event.Name});
				console.log("handler: Update DB: Done");
				// Applying changes to S3 bucket
				console.log("handler: Update S3 bucket...");
				try {
				  await moveFolder(event, res);
				  console.log("handler: moveFolder done")
				  console.log("handler: Update S3 bucket: Done");
				  res.json({
					  status: 200, 
					  message: 'Update successful',
				  });
				} catch (error) {
				  console.log("handler: " , error);
				  res.json({
					  status: 400, 
					  message: 'Update failed',
				  });
				}
				
				break;
			case 'Delete':
				try {
					console.log("handler: Delete DB entry...");
					await Table.deleteOne({id: event.id, Name: event.Name});
					console.log("handler: Delete DB entry: Done");
					// Deleting the s3 object
					console.log("handler: Delete S3 object...");
					await deleteFolder(event, res);
					console.log("handler: Delete S3 object: Done");
					res.json({
					  status: 200, 
					  message: 'Update successful',
				  });
				} catch (e) {
					console.log("handler: " , e);
					res.json({
					  status: 400, 
					  message: 'Update failed',
				  });
				}
				break;
      case 'Sync':
        console.log('Sync');
        sync(res);
        break;
      default:
        console.log('Invalid switch case.');
		res.json({
			status: 401, 
		    message: 'Request failed',
	    });
        break;
		}

	} catch (err) {
		console.log(err);
		res.json({
			status: 401, 
		    message: 'Request failed',
	    });
	}

}

// TODO: Sync Rekognition collection  with db Table
// TODO: Sync s3 bucket with db Table
async function sync(res) {
  var dbIds = [];
  var dbNames = [];
  var dbFaceIds = [];
  var s3Params = {
    Bucket: bucket
  }
  var s3Names = [];
  var s3Ids = [];
  var s3FileNames = [];
  var s3Outliers = [];
  var rekParams = {
   CollectionId: "collection0",
  };
  var rekFaceIds = [];

  var s3Promise = await s3.listObjectsV2(s3Params, async (err, data) => {
    if (err) {
      console.log(err, err.stack);
    } else {
      // List all the s3 data
      data.Contents.forEach(item => {
        var docData = item.Key.split('/');
        s3Names.push(docData[0]);
        s3Ids.push(docData[1]);
        s3FileNames.push(docData[2])
      });
    }
  }).promise();

  var dbPromise = await Table.find(null, (err, docs) => {
    docs.forEach(doc => {
      dbIds.push(doc.id);
      dbNames.push(doc.Name);
      doc.FaceID.forEach(faceId => {
        dbFaceIds.push(faceId);
      });
    });
  });

  var rekPromise = await rekognition.listFaces(rekParams, function(err, data) {
    if (err) console.log(err, err.stack); // an error occurred
    else {
      data.Faces.forEach(item => {
        rekFaceIds.push(item.FaceId);           // successful response
      });
    }
  }).promise();

  await Promise.all([s3Promise, dbPromise, rekPromise])
  .then(res => {
    // TODO: Sync collection using FaceIds to identify faces for deletion
    syncCollection(rekFaceIds, dbFaceIds);

    // TODO: Sync s3 using Ids to identify objects for deletion
    syncS3(s3Ids, dbIds);
  })
  .then(res => {
    console.log("And then");
    console.log("Done");
    // TODO: Identify empty s3 folders and delete them
  })
  .catch(err => {
    console.log("sync exception: " + err);
  })

  res.send({
    status: 200,
    message:'Sync OK'
  });

}

async function syncCollection(rekFaceIds, dbFaceIds) {
  console.log("dbFaceIds");
  console.log(dbFaceIds);
  console.log("rekFaceIds");
  console.log(rekFaceIds);
  var rekOutliers = [];

  rekFaceIds.forEach(rekFaceId => {
    if (!dbFaceIds.includes(rekFaceId)) {    // If something is missing from the collection
      rekOutliers.push(rekFaceId);
    }
  });
  console.log("rekOutliers");
  console.log(rekOutliers);
  var params = {
    CollectionId: "collection0",
    FaceIds: rekOutliers,
   };
   if (rekOutliers.length > 0) {
     rekognition.deleteFaces(params, function(err, data) {
       if (err) console.log(err); // an error occurred
       else     console.log(data);           // successful response
     });
   }

   dbFaceIds.forEach(dbFaceId => {
     if (!rekFaceIds.includes(dbFaceId)) {    // If something is missing from the db (unlikely)
       console.log(dbFaceId);
       // TODO: Search s3 for the ExternalImageId (to get the name)
       // TODO: Add the faceId, name, and a new working id to the db if you find it, othertwise clean up
     }
   });
}

async function syncS3(s3Ids, dbIds) {
  console.log("s3Ids");
  console.log(s3Ids);
  console.log("dbIds");
  console.log(dbIds);

  s3Ids.forEach(s3Id => {
    if (!dbIds.includes(s3Id)) {  // If something is missing from the bucket
      console.log(s3Id);
      // TODO: Enrol that face, i.e.,
      // TODO: Generate a FaceId based on the s3 object in the collection0
      // TODO: Get the Name from the name of the s3 object
      // TODO: Generate a random string id,
      // TODO: Make a json and push it to the db
    }
  });

  dbIds.forEach(dbId => {
    if (!s3Ids.includes(dbId)) {     // If something is missing from the db
      console.log(dbId);
      // TODO: Search the collection0 for the ExternalImageID
      // TODO: Get the FaceID and Name. Generate a random string id. Make json
      // TODO: Push json to db
    }
  });
}

async function listItems(obj, names, ids, roles, callback) {

	await Table.find(null, (err,doc) => {
		doc.forEach(item => {
			names.push(item.Name);
			ids.push(item.id);
			roles.push(item.Role);
		});
	});

	obj.Name = names;
	obj.id = ids;
	obj.Role = roles;
	callback.json(obj);

  console.log("listItems: done");

}

function moveFolder(event, callback) {
  var IDs = [];
  var names = [];
  var params = {
    Bucket: bucket
  };

  return s3.listObjectsV2(params, function (err, data){
    if (err) {
      console.log(err, err.stack);
    } else {
      // What the current directory looks like
      data.Contents.forEach(item => {
        IDs.push(item.Key.split('/')[1]);
        names.push(item.Key.split('/')[0]);
      });
    }
  }).promise()
  .then(res => {
    var destinationFolder;
    var folderToMove;
    // Loop through all of the existing IDs
    for (var i = 0; i < IDs.length; i++) {
      // If a name has changed, update the folder name
      if (IDs[i] == event.id && names[i] != event.Name) {
        // Copy contents to destination folder
        destinationFolder = event.Name + '/' + event.id;
        folderToMove = names[i] + '/' + event.id;
        console.log("moveFolder: Folder to move: " + folderToMove);
        console.log("moveFolder: Destination: " + destinationFolder);
        break;
      }
    }
    return s3CopyFolder(bucket, folderToMove + '/', destinationFolder + '/');
  }).then(async res => {
    var folderToDelete;
    for (var i = 0; i < IDs.length; i++) {
      if (IDs[i] == event.id && names[i] != event.Name) {
        // Delete source folder
        folderToDelete = names[i] + '/' + event.id;
        break;
      }
    }
    return emptyBucket(bucket, folderToDelete, callback);
  }).then(res => {
    var directoryToDelete;
    var params;
    for (var i = 0; i < IDs.length; i++) {
      if (IDs[i] == event.id /**&& names[i] != event.Name*/) {
        // Deletes the name directory if it is empty
        directoryToDelete = names[i] + '/';
        params = {
          Bucket: bucket,
          Key: directoryToDelete
         };
        break;
      }
    }

    console.log("deleteFolder: params", params);

    return s3.deleteObject(params, function(err, data) {
      if (err) console.log(err, err.stack); // an error occurred
      else {
         console.log("moveFolder: Empty name directory deleted");
         // callback.json({message: "OK"});           // successful response
      }
    }).promise();
  }).catch((rejectionReason) => {
    console.log("moveFolder exception: " + rejectionReason);
  });
}


function deleteFolder(event, callback) {
  var IDs = [];
  var names = [];
  var params = {
    Bucket: bucket
  };

  return s3.listObjectsV2(params, function (err, data){
    if (err) {
      console.log("listObjs: " , err);
    } else {
      data.Contents.forEach(item => {
        IDs.push(item.Key.split('/')[1]);
        names.push(item.Key.split('/')[0]);
      });
    }
  }).promise()
  .then(res => {
    var folderToDelete;
    for (var i = 0; i < IDs.length; i++) {
      if (IDs[i] == event.id && names[i] == event.Name) {
        // Deleting source folder objects
        folderToDelete = names[i] + '/' + event.id;
        break;
      }
    }
    return emptyBucket(bucket, folderToDelete, callback);
  }).then(res => {
    var params;
    var directoryToDelete;
    for (var i = 0; i < IDs.length; i++) {
      if (IDs[i] == event.id /**&& names[i] != event.Name*/) {
        // Deletes the name directory if it is empty
        directoryToDelete = names[i] + '/';
        params = {
          Bucket: bucket,
          Key: directoryToDelete
         };
        break;
      }
    }

    console.log("deleteFolder: params", params);

    return s3.deleteObject(params, function(err, data) {
      if (err) console.log("deleteFolder: delObjs: ", err); // an error occurred
      else     return; /**callback.json({message: "OK", data: data});*/           // successful response
    }).promise();

    //
    // if (params) {
    //
    // } else {
    //   console.log("")
    //   return;
    // }


  }).catch(err => {
    console.log("deleteFolder exception: ", err);
  });
}


async function s3CopyFolder(bucket, source, dest) {
  // sanity check: source and dest must end with '/'
  if (!source.endsWith('/') || !dest.endsWith('/')) {
    return Promise.reject(new Error('source or dest must ends with fwd slash'));
  }

  // plan, list through the source, if got continuation token, recursive
  const listResponse = await s3.listObjectsV2({
    Bucket: bucket,
    Prefix: source,
    Delimiter: '/',
  }).promise();

  // copy objects
  await Promise.all(
    listResponse.Contents.map(async (file) => {
      await s3.copyObject({
        Bucket: bucket,
        CopySource: `${bucket}/${file.Key}`,
        Key: `${dest}${file.Key.replace(listResponse.Prefix, '')}`,
        ACL: "public-read",
      }).promise();
    }),
  );

  // recursive copy sub-folders
  await Promise.all(
    listResponse.CommonPrefixes.map(async (folder) => {
      await s3CopyFolder(
        bucket,
        `${folder.Prefix}`,
        `${dest}${folder.Prefix.replace(listResponse.Prefix, '')}`,
      );
    }),
  );

  console.log("s3CopyFolder: Copy done");
  return Promise.resolve('ok');
}


async function emptyBucket(bucketName, source, callback){
  console.log("emptyBucket: start");
  var params = {
    Bucket: bucketName,
    Prefix: source + '/'                 // Folder to delete
  };
  await s3.listObjects(params, function(err, data) {
    if (err) console.log("list err: ", err);
    // if (data.Contents.length == 0) return null;
    params = {Bucket: bucketName};
    params.Delete = {Objects:[]};
    data.Contents.forEach(content => {
      params.Delete.Objects.push({Key: content.Key});
    });
  }).promise();

  console.log(params.Delete.Objects.length);

  if (params.Delete.Objects.length != 0) {
    var prom = s3.deleteObjects(params, function(err, data) {
      console.log("emptyBucket: deleteObjects");
      if (err) console.log("emptyBucket: delObjs error:", err);
      //if(data.Contents.length == 1000)emptyBucket(bucketName,callback);
      else console.log(data); //callback.json({message:"OK"});
    }).promise();

  } else {
    var prom = null;
  }

  console.log("emptyBucket: finish");
  return prom;


}
