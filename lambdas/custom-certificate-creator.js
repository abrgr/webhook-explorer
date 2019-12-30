const url = require('url');
const https = require('https');
const ACM = require('aws-sdk/clients/acm');
const Route53 = require('aws-sdk/clients/route53');

const apiVersion = '2019-09-21';
const acm = new ACM({ apiVersion });
const route53 = new Route53({ apiVersion });

exports.handler = async function handler(event, context) {
  const { RequestType, RequestId, ResponseURL, StackId, LogicalResourceId } = event;

  try {
    if ( RequestType === 'Create' ) {
      const response = await handleCreate(event);
      await sendResponse(response, ResponseURL);
    } else if ( RequestType === 'Update' ) {
      await handleDelete(event);
      const response = await handleCreate(event);
      await sendResponse(response, ResponseURL);
    } else if ( RequestType === 'Delete' ) {
      const response = await handleDelete(event);
      await sendResponse(response, ResponseURL);
    }
  } catch ( err ) {
    console.error('Failed', err);
    const errResponse = {
      Status: 'FAILED',
      Reason: err.toString(),
      PhysicalResourceId: ['fake', StackId, LogicalResourceId, RequestId].join('/'),
      StackId,
      RequestId,
      LogicalResourceId
    };
    await sendResponse(errResponse, ResponseURL);
  }
};

async function handleDelete(event) {
  const { RequestId, StackId, LogicalResourceId, PhysicalResourceId } = event;
  const { HostedZoneId } = event.ResourceProperties;

  if ( !PhysicalResourceId.startsWith('fake/') ) {
    const cnameRecord = await getCertCname(PhysicalResourceId);

    try {
      await deleteDomain(HostedZoneId, cnameRecord);
    } catch ( err ) {
      // ignore the error if domain isn't found
      if ( (err.message || '').indexOf('not found') <= 0) {
        throw err;
      }
    }

    await acm.deleteCertificate({
      CertificateArn: PhysicalResourceId
    }).promise();
  }

  const response = {
    Status: 'SUCCESS',
    RequestId,
    LogicalResourceId,
    StackId,
    PhysicalResourceId
  };

  return response;
}

async function handleCreate(event) {
  const { RequestId, LogicalResourceId, StackId } = event;
  const { DomainName, HostedZoneId } = event.ResourceProperties;

  const CertificateArn = await createCert(HostedZoneId, DomainName);

  const response = {
    Status: 'SUCCESS',
    RequestId,
    LogicalResourceId,
    StackId,
    PhysicalResourceId: CertificateArn
  };

  return response;
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
      console.error("Failed to send response", error);
      reject(error);
    });

    // write data to request body
    request.write(responseBody);
    request.end();
  });
}

async function createCert(HostedZoneId, DomainName) {
  const { CertificateArn } = await acm.requestCertificate({
    DomainName,
    ValidationMethod: 'DNS'
  }).promise();

  const cnameRecord = await getCertCname(CertificateArn);

  await verifyDomain(HostedZoneId, cnameRecord);

  await acm.waitFor('certificateValidated', { CertificateArn }).promise();

  return CertificateArn;
}

async function delay(seconds) {
  return new Promise((resolve, reject) => {
    setTimeout(resolve, seconds * 1000);
  });
}

async function getCertCname(CertificateArn) {
  await delay(10); // wait for aws to generate the cname we need
  const cert = await acm.describeCertificate({
    CertificateArn
  }).promise();

  return cert.Certificate.DomainValidationOptions.find(x => !!x.ResourceRecord).ResourceRecord;
}

async function verifyDomain(HostedZoneId, cnameRecord) {
  try {
    return await route53.changeResourceRecordSets({
      HostedZoneId,
      ChangeBatch: {
        Comment: 'Domain Verification',
        Changes: [{
          Action: 'CREATE',
          ResourceRecordSet: {
            Name: cnameRecord.Name,
            ResourceRecords: [{
              Value: cnameRecord.Value
            }],
            TTL: 300,
            Type: 'CNAME'
          }
        }]
      }
    }).promise();
  } catch ( err ) {
    if ( err.code === 'InvalidChangeBatch' && err.message && err.message.indexOf('already exists') >= 0 ) {
      return Promise.resolve();
    }

    throw err;
  }
}

async function deleteDomain(HostedZoneId, cnameRecord) {
  return await route53.changeResourceRecordSets({
    HostedZoneId,
    ChangeBatch: {
      Comment: 'Domain Verification',
      Changes: [{
        Action: 'DELETE',
        ResourceRecordSet: {
          Name: cnameRecord.Name,
          ResourceRecords: [{
            Value: cnameRecord.Value
          }],
          TTL: 300,
          Type: 'CNAME'
        }
      }]
    }
  }).promise();
}
