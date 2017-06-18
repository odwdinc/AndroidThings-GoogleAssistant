const functions = require('firebase-functions');


const admin = require('firebase-admin');
admin.initializeApp(functions.config().firebase);


var TimeOutwaitTill;
var Fulfillment = null;
var intentName = null;

var MaxTimeOut = 2;

function databaseTest(response) {	
  	admin.database().ref('/message/'+intentName+"/Fulfillment").once('value').then(function(snapshot) {
		console.log("has Fulfillment " + snapshot.val());
		if(snapshot.val() != null && snapshot.val() != "NotSet"){

			Fulfillment = snapshot.val();
			response.json(Fulfillment);
		}else{
			setTimeout(databaseTestLoop, 500, response)
		}
	  	});
}

function databaseTestLoop(response) {	
  console.log('database Test Loop');
  if(Fulfillment == null && TimeOutwaitTill > new Date() ){
    setTimeout(databaseTest, 0, response);
  }else if (TimeOutwaitTill < new Date() ){
    response.send("database Test Time Outed");
  }
  

}


// // Create and Deploy Your First Cloud Functions
// // https://firebase.google.com/docs/functions/write-firebase-functions

 exports.helloWorld = functions.https.onRequest((request, response) => {
  
 if(request.body.result != null){
 	intentName = request.body.result.metadata.intentName;
  	if(intentName !=null){
  
  	admin.database().ref('/message/').once('value').then(function(snapshot) {
  		var toset = snapshot.val();
  		toset["intentName"] = intentName;
  		toset[intentName] = request.body.result.parameters;
  		toset[intentName]["Fulfillment"] = "NotSet";//{"Test":"Hello World"};
  		admin.database().ref('/message/').set(toset);
  		TimeOutwaitTill = new Date(new Date().getTime() + MaxTimeOut * 1000);
  		setTimeout(databaseTestLoop, 0, response);
	});
  	
	}else{
		response.send("No intentName");
	}
}
 });
