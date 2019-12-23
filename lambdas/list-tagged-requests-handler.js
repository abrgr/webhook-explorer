const {
  getUserFromEvent,
  response,
  auditKeyToFingerprintAndTag,
  getTagForFavorite,
  isUserAuthorizedToReadFolder,
  folderForTag
} = require('./common');
const { getNextListing } = require('./ymd-lister');

exports.handler = async function handler(event, context) {
  const { uid } = getUserFromEvent(event);
  const { ymd, token } = event.queryStringParameters || {};

  const page = await getNextListing('audit', ymd, token, auditKeyToFingerprintAndTag);

  return response(200, {}, JSON.stringify(generatePage(uid, page)));
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
      const prev = tagsForFingerprint[fingerprint] || { fav: false, tags: [] };

      if ( tag === favTag ) {
        prev.fav = true;
      } else {
        prev.tags = prev.tags.concat([tag]);
      }

      tagsForFingerprint[fingerprint] = prev;

      return tagsForFingerprint;
    },
    {}
  );

  return {
    tagsByFingerprint,
    nextReq
  };
}
