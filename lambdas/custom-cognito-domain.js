const url = require('url');
const https = require('https');
const CognitoIdentityServiceProvider = require('aws-sdk/clients/cognitoidentityserviceprovider');

const apiVersion = '2019-09-21';
const cognitoIdentityServiceProvider = new CognitoIdentityServiceProvider({
  apiVersion
});

exports.handler = async function handler(event) {
  const {
    RequestType,
    ResourceProperties,
    RequestId,
    LogicalResourceId,
    StackId,
    PhysicalResourceId,
    ResponseURL
  } = event;
  const { UserPoolId, Domain, CertificateArn } = ResourceProperties;

  try {
    let physicalId = null;
    if (RequestType === 'Create') {
      const response = await cognitoIdentityServiceProvider
        .createUserPoolDomain({
          UserPoolId,
          Domain,
          CustomDomainConfig: {
            CertificateArn
          }
        })
        .promise();
      physicalId = response.CloudFrontDomain;
    } else if (RequestType === 'Update') {
      await deleteUserPoolDomain(event.OldResourceProperties.Domain);

      const response = await cognitoIdentityServiceProvider
        .createUserPoolDomain({
          UserPoolId,
          Domain,
          CustomDomainConfig: {
            CertificateArn
          }
        })
        .promise();
      physicalId = response.CloudFrontDomain;
    } else if (RequestType === 'Delete') {
      await deleteUserPoolDomain(cognitoIdentityServiceProvider, Domain);
    }

    const response = {
      Status: 'SUCCESS',
      RequestId,
      LogicalResourceId,
      StackId,
      PhysicalResourceId: physicalId || PhysicalResourceId
    };

    await sendResponse(response, ResponseURL);
  } catch (err) {
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
};

async function deleteUserPoolDomain(Domain) {
  const response = await cognitoIdentityServiceProvider
    .describeUserPoolDomain({
      Domain
    })
    .promise();

  if (response.DomainDescription.Domain) {
    await cognitoIdentityServiceProvider
      .deleteUserPoolDomain({
        UserPoolId: response.DomainDescription.UserPoolId,
        Domain
      })
      .promise();
  }
}

async function sendResponse(response, ResponseURL) {
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
      console.error('Failed to send response', error);
      reject(error);
    });

    // write data to request body
    request.write(responseBody);
    request.end();
  });
}
