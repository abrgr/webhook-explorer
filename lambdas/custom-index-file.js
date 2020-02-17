const url = require('url');
const https = require('https');
const S3 = require('aws-sdk/clients/s3');

const apiVersion = '2019-09-21';
const s3 = new S3({ apiVersion });

exports.handler = async function handler(event, context) {
  const { RequestType, ResourceProperties, RequestId, LogicalResourceId, StackId, PhysicalResourceId, ResponseURL } = event;
  const { Version, HandlerDomains, UserPoolId, ClientId, AppWebDomain, RedirectUri, Bucket } = ResourceProperties;

  try {
    const Key = 'web/index.html';
    if ( RequestType === 'Create' || RequestType === 'Update' ) {
      await s3.putObject({
        Bucket,
        Key,
        Body: getContent(Version, HandlerDomains, ClientId, AppWebDomain, RedirectUri, UserPoolId),
        ContentType: 'text/html'
      }).promise();
    } else if ( RequestType === 'Delete' ) {
      await s3.deleteObject({
        Bucket,
        Key
      }).promise();
    }

    const response = {
      Status: 'SUCCESS',
      RequestId,
      LogicalResourceId,
      StackId,
      PhysicalResourceId: Bucket ? `s3://${Bucket}/${Key}` : PhysicalResourceId
    };
    
    await sendResponse(response, ResponseURL);
  } catch ( err ) {
    console.error('Failed', err);
    const errResponse = {
      Status: 'FAILED',
      Reason: err.toString(),
      PhysicalResourceId: [StackId, LogicalResourceId, RequestId].join('/'),
      StackId,
      RequestId,
      LogicalResourceId
    };
    await sendResponse(errResponse, ResponseURL);
  }
}

function getContent(version, handlerDomains, clientId, appWebDomain, redirectUri, userPoolId) {
  const rogoUrl = url.parse(redirectUri);
  rogoUrl.path = "/api/";

  return `
    <!DOCTYPE html>
    <html style="width:100%;height:100%;">
      <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <link href="/styles/style.css" rel="stylesheet" type="text/css">
        <link href="https://fonts.googleapis.com/css?family=Roboto&display=swap" rel="stylesheet">
        <script id="config" type="application/json">
          {
            "version": "${version}",
            "rogoDomain": "${rogoUrl.host}",
            "rogoApiUrl": "${url.format(rogoUrl)}",
            "handlerDomains": ${JSON.stringify(handlerDomains)},
            "cognito": {
              "ClientId": "${clientId}",
              "AppWebDomain": "${appWebDomain}",
              "TokenScopesArray": ["email", "profile","openid", "aws.cognito.signin.user.admin"],
              "RedirectUriSignIn": "${redirectUri}",
              "RedirectUriSignOut": "${redirectUri}",
              "IdentityProvider": "Cognito",
              "UserPoolId": "${userPoolId}"
            }
          }
        </script>
      </head>
      <body style="width:100%;height:100%;">
        <div style="width: 100%;height:100%;" id="app"></div>
        <script src="/js/main.js" type="text/javascript"></script>
      </body>
    </html>
  `;
}

async function sendResponse(response, ResponseURL) {
  console.log('Sending response', response);
  return new Promise((resolve, reject) => {
    const parsedUrl = url.parse(ResponseURL);
    const responseBody = JSON.stringify(response);
    const options = {
      hostname: parsedUrl.hostname,
      port: parsedUrl.port || 443,
      path: parsedUrl.path,
      rejectUnauthorized: parsedUrl.hostname !== 'localhost',
      method: 'PUT',
      headers: {
        'Content-Type': '',
        'Content-Length': responseBody.length
      }
    };

    const request = https.request(options, response => {
      response.on('data', () => {
        // noop
      });

      response.on('end', () => {
        resolve();
      });
    });

    request.on('error', error => {
      console.error("Failed to send response", error);
      reject(error);
    });

    // write data to request body
    request.write(responseBody);
    request.end();
  });
}
