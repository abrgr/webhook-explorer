const Cognito = require('aws-sdk/clients/cognitoidentityserviceprovider');
const {
  response,
  getUserFromEvent,
  cognitoUserToUser
} = require('./common');

const cognito = new Cognito({ apiVersion: '2019-09-21' });
const poolId = process.env.COGNITO_USER_POOL_ID;

exports.handler = async function handler(event, context) {
  const { user: { email, role }} = JSON.parse(event.body) || {};
  const { permissions: { canAdminUsers }} = getUserFromEvent(event);

  if ( !canAdminUsers ) {
    return response(401, {}, JSON.stringify({ error: 'Unauthorized' }));
  }

  try {
    const { User: user } = await cognito.adminCreateUser({
      UserPoolId: poolId,
      Username: email.replace('@', '_'),
      DesiredDeliveryMediums: ['EMAIL'],
      ForceAliasCreation: true,
      UserAttributes: [
        {
          Name: 'email',
          Value: email
        },
        {
          Name: 'email_verified',
          Value: 'true'
        },
        {
          Name: 'custom:role',
          Value: role
        }
      ]
    }).promise();

    return response(200, {}, JSON.stringify({ user: cognitoUserToUser(user), success: true }));
  } catch ( err ) {
    console.error('Failed to create user', err);
    const errors = err.errors ? err.errors : [err];
    const errMsgs = errors.filter(e => e.code === 'InvalidParameterType')
                          .map(e => e.message);
    return response(400, {}, JSON.stringify({ error: { msg: "Could not create user", messages: errMsgs }}));
  }
};
