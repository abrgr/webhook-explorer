const Cognito = require('aws-sdk/clients/cognitoidentityserviceprovider');
const {
  response,
  getUserFromEvent,
  cognitoUserToUser,
} = require('./common');

const cognito = new Cognito({ apiVersion: '2019-09-21' });
const poolId = process.env.COGNITO_USER_POOL_ID;

exports.handler = async function handler(event, context) {
  const { token } = event.queryStringParameters || {};
  const { permissions: { canAdminUsers }} = getUserFromEvent(event);

  if ( !canAdminUsers ) {
    return response(401, {}, JSON.stringify({ error: 'Unauthorized' }));
  }

  const { Users: users, PaginationToken: nextToken } = await cognito.listUsers({
    UserPoolId: poolId,
    PaginationToken: token
  }).promise();

  const page = {
    users: users.map(cognitoUserToUser),
    nextReq: nextToken ? { token: nextToken } : null
  };

  return response(200, { 'Cache-Control': `max-age=${300}` }, JSON.stringify(page));
};
