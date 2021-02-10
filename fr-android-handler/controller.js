Table = require('./inputModel');


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
			console.log("Done");
	});
};


exports.handler = async function (req, res) {
	
	//console.log(req.body);
	
	switch (req.body.command) {
		
		case 'createNewId':
			
			if(!await Table.exists({ id: req.body.workingId })) {
				console.log("Adding new ID...");
				
				// Adding data to appropriate collection
				var input = new Table(); 
				input.id = req.body.workingId;
				input.Name = req.body.Name;
				input.FaceID = []; 
				
				// Save the doc and check for errors 
				input.save((err) => {
					if (err) {
						res.json(err);
					}
					res.json({
						message: 'Added successfully', 
						data: input
					});
				});
				console.log('Done');
				
			} else {
				
				console.log("Could not create new ID. Existing document with the same ID found:");
				console.log(await Table.find({id: req.body.workingId}));
				console.log("Done.");
				res.json({
						status: "OK",
						message: "Avoided duplicate ID."
				});
				
			}
			
			break;
		
		case 'addFace':
			console.log("Pushing FaceID...");
			// Push FaceID to document if it doesn't already exist 
			await Table.find({id: req.body.workingId}, async (err, doc) => {
				
				if (!doc[0].FaceID.includes(req.body.FaceID && req.body.FaceID != null)) {
					await Table.updateOne(
							{ id: req.body.workingId }, 
							{ $push: { FaceID: req.body.FaceID } 
						});	
					res.json({
							status: "OK",
							message: "Added FaceID"
					});
					console.log('Done');
				} else {
					console.log("Existing/invalid FaceID '" + req.body.FaceID + "' omitted.");
					res.json({
							status: "OK",
							message: "Avoided pushing duplicate FaceID"
					});
				}
			});

			break;
	}
	
	
}
