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
      await handleCreate(event);
    } else if ( RequestType === 'Update' ) {
      console.log('Ignoring update');
    } else if ( RequestType === 'Delete' ) {
      await handleDelete(event);
    }
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
};

async function handleDelete(event) {
  console.log('Handling delete...');
  const { RequestId, StackId, LogicalResourceId, PhysicalResourceId, ResponseURL } = event;
  const { HostedZoneId } = event.ResourceProperties;

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

  const response = {
    Status: 'SUCCESS',
    RequestId,
    LogicalResourceId,
    StackId,
    PhysicalResourceId
  };

  await sendResponse(response, ResponseURL);
}

async function handleCreate(event) {
  console.log('Handling create...');

  const { ResponseURL, RequestId, LogicalResourceId, StackId } = event;
  const { DomainName, HostedZoneId } = event.ResourceProperties;

  const CertificateArn = await createCert(HostedZoneId, DomainName);

  const response = {
    Status: 'SUCCESS',
    RequestId,
    LogicalResourceId,
    StackId,
    PhysicalResourceId: CertificateArn
  };

  await sendResponse(response, ResponseURL);
}

async function sendResponse(response, ResponseURL) {
  console.log('Sending response');
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
  console.log('Requesting cert');
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
  console.log('Describing cert');
  const cert = await acm.describeCertificate({
    CertificateArn
  }).promise();

  return cert.Certificate.DomainValidationOptions.find(x => !!x.ResourceRecord).ResourceRecord;
}

async function verifyDomain(HostedZoneId, cnameRecord) {
  console.log('Verifying domain');
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
}

async function deleteDomain(HostedZoneId, cnameRecord) {
  console.log('Deleting domain');
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