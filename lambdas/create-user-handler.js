const Cognito = require('aws-sdk/clients/cognitoidentityserviceprovider');
const {
  response,
  getUserFromEvent,
  cognitoUserToUser
} = require('./common');

const cognito = new Cognito({ apiVersion: '2019-09-21' });
const poolId = process.env.COGNITO_USER_POOL_ID;

exports.handler = async function handler(event, context) {
  const { email, role } = event.queryStringParameters || {};
  const { permissions: { canAdminUsers }} = getUserFromEvent(event);

  if ( !canAdminUsers ) {
    return response(401, {}, JSON.stringify({ error: 'Unauthorized' }));
  }

  const { User: user } = cognito.adminCreateUser({
    UserPoolId: poolId,
    Username: email,
    DesiredDeliveryMediums: ['EMAIL'],
    ForceAliasCreation: true,
    UserAttributes: [
      {
        Name: 'email',
        Value: email
      },
      {
        Name: 'email_verified',
        Value: true
      }
    ]
  }).promise();

  return response(200, { 'Cache-Control': `max-age=${cacheSeconds}` }, JSON.stringify({ user: cognitoUserToUser(user), success: true }));
};
