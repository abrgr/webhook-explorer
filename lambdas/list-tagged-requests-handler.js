const {
  getUserFromEvent,
  response,
  auditKeyToFingerprintTagAndDate,
  getTagForFavorite,
  isUserAuthorizedToReadFolder,
  folderForTag,
  isPrivateTag,
  unNamespacedPrivateTag,
  fingerprintTagAndDateToUserVisibleTags
} = require('./common');
const { getNextListing } = require('./ymd-lister');

exports.handler = async function handler(event, context) {
  const { uid } = getUserFromEvent(event);
  const { ymd, token } = event.queryStringParameters || {};

  const page = await getNextListing('audit', ymd, token, auditKeyToFingerprintTagAndDate);

  const cacheSeconds = (ymd || token) ? 300 : 5;
  return response(200, { 'Cache-Control': `max-age=${cacheSeconds}` }, JSON.stringify(generatePage(uid, page)));
};

function generatePage(uid, page) {
  if ( !page ) {
    return null;
  }

  const { items, nextReq } = page;
  const tagsByFingerprint = fingerprintTagAndDateToUserVisibleTags(uid, items);
  const earliestDatedItem = items.sort((a, b) => (a.date < b.date) ? -1 : (a.date === b.date ? 0 : 1))[0];

  return {
    tagsByFingerprint,
    nextReq,
    earliestDate: earliestDatedItem ? earliestDatedItem.date : null
  };
}
