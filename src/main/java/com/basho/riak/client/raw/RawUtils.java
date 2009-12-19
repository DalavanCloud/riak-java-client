package com.basho.riak.client.raw;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.basho.riak.client.RiakLink;
import com.basho.riak.client.util.Constants;
import com.basho.riak.client.util.LinkHeader;
import com.basho.riak.client.util.Multipart;

/**
 * Utility functions specific to the Raw interface
 */
public class RawUtils {

    /**
     * Parse a link header into a {@link RiakLink}. See {@link LinkHeader}.
     * 
     * @param header
     *            The HTTP Link header value.
     * @return Collection of {@link RiakLink} objects constructed from the links
     *         in header.
     */
    public static Collection<RiakLink> parseLinkHeader(String header) {
        Collection<RiakLink> links = new ArrayList<RiakLink>();
        Map<String, Map<String, String>> parsedLinks = LinkHeader.parse(header);
        for (String url : parsedLinks.keySet()) {
            RiakLink link = parseOneLink(url, parsedLinks.get(url));
            if (link != null) {
                links.add(link);
            }
        }
        return links;
    }

    /**
     * Create a {@link RiakLink} object from a single parsed link from the Link
     * header
     * 
     * @param url
     *            The link URL
     * @param params
     *            The link parameters
     * @return {@link RiakLink} object
     */
    private static RiakLink parseOneLink(String url, Map<String, String> params) {
        String tag = params.get(Constants.RAW_LINK_TAG);
        if (tag != null) {
            String[] parts = url.split("/");
            if (parts.length >= 2)
                return new RiakLink(parts[parts.length - 2], parts[parts.length - 1], tag);
        }
        return null;
    }

    /**
     * Extract only the user-specified metadata headers from a header set: all
     * headers prefixed with X-Riak-Meta-. The prefix is removed before
     * returning.
     * 
     * @param headers
     *            The full HTTP header set from the response
     * @return Map of all headers prefixed with X-Riak-Meta- with prefix
     *         removed.
     */
    public static Map<String, String> parseUsermeta(Map<String, String> headers) {
        Map<String, String> usermeta = new HashMap<String, String>();
        for (String header : headers.keySet()) {
            if (header.startsWith(Constants.HDR_USERMETA_PREFIX)) {
                usermeta.put(header.substring(Constants.HDR_USERMETA_PREFIX.length()), headers.get(header));
            }
        }
        return usermeta;
    }

    /**
     * Convert a multipart/mixed document to a list of {@link RawObject}s.
     * 
     * @param bucket
     *            original object's bucket
     * @param key
     *            original object's key
     * @param docHeaders
     *            original document's headers
     * @param docBody
     *            original document's body
     * @return List of RawObjects represented by the multipart document
     */
    public static List<RawObject> parseMultipart(String bucket, String key, Map<String, String> docHeaders,
                                                 String docBody) {

        String vclock = docHeaders.get(Constants.HDR_VCLOCK);

        List<Multipart.Part> parts = Multipart.parse(docHeaders, docBody);
        List<RawObject> objects = new ArrayList<RawObject>();
        if (parts != null) {
            for (Multipart.Part part : parts) {
                Map<String, String> headers = part.getHeaders();
                Collection<RiakLink> links = parseLinkHeader(headers.get(Constants.HDR_LINK));
                Map<String, String> usermeta = parseUsermeta(headers);
                String location = headers.get(Constants.HDR_LOCATION);
                String partBucket = bucket;
                String partKey = key;

                if (location != null) {
                    String[] locationParts = location.split("/");
                    if (locationParts.length >= 2) {
                        partBucket = locationParts[locationParts.length - 2];
                        partKey = locationParts[locationParts.length - 1];
                    }
                }

                RawObject o = new RawObject(partBucket, partKey, part.getBody(), links, usermeta,
                                            headers.get(Constants.HDR_CONTENT_TYPE), vclock,
                                            headers.get(Constants.HDR_LAST_MODIFIED), headers.get(Constants.HDR_ETAG));
                objects.add(o);
            }
        }
        return objects;
    }

}
