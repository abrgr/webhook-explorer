const {
  getUserFromEvent,
  response,
  auditKeyToFingerprintTagAndDate,
  getTagForFavorite,
  isUserAuthorizedToReadFolder,
  folderForTag,
  isPrivateTag,
  unNamespacedPrivateTag
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
  const favTag = getTagForFavorite(uid);
  const filteredItems = items.filter(({ tag }) => isUserAuthorizedToReadFolder(uid, folderForTag(tag))) 
  const tagsByFingerprint = filteredItems.reduce(
    (tagsForFingerprint, { fingerprint, tag }) => {
      const prev = tagsForFingerprint[fingerprint] || { fav: false, privateTags: [], publicTags: [] };

      if ( tag === favTag ) {
        prev.fav = true;
      } else if ( isPrivateTag(tag) ) {
        prev.privateTags = prev.privateTags.concat([unNamespacedPrivateTag(tag)]);
      } else {
        prev.publicTags = prev.publicTags.concat([tag]);
      }

      tagsForFingerprint[fingerprint] = prev;

      return tagsForFingerprint;
    },
    {}
  );
  const earliestDatedItem = items.sort((a, b) => (a < b) ? -1 : (a === b ? 0 : 1))[0];

  return {
    tagsByFingerprint,
    nextReq,
    earliestDate: earliestDatedItem ? earliestDatedItem.date : null
  };
}
