/*
 * This file is provided to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.basho.riak.client.jiak;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.basho.riak.client.RiakLink;
import com.basho.riak.client.RiakObject;
import com.basho.riak.client.response.StoreResponse;
import com.basho.riak.client.util.Constants;

/**
 * Implementation of RiakObject which interprets objects retrieved from Riak's
 * Jiak interface. Internally, the value, links, and user-defined metadata are
 * all stored as JSON objects and converted to their Java object equivalents on
 * request.
 */
public class JiakObject implements RiakObject {

    private String bucket;
    private String key;
    private JSONObject value = new JSONObject();
    private JSONArray links = new JSONArray();
    private JSONObject usermeta = new JSONObject();
    private String vclock;
    private String lastmod;
    private String vtag;

    /**
     * Build a JiakObject from existing JSON. Throws {@link JSONException} if
     * any required fields (bucket or key) are missing.
     * 
     * @param object
     *            JSON representation of the Jiak object
     * @throws JSONException
     *             If bucket or key fields are missing in the JSON
     */
    public JiakObject(JSONObject object) throws JSONException {
        this(object.getString(Constants.JIAK_FL_BUCKET), object.getString(Constants.JIAK_FL_KEY),
             object.optJSONObject(Constants.JIAK_FL_VALUE), object.optJSONArray(Constants.JIAK_FL_LINKS),
             null, object.optString(Constants.JIAK_FL_VCLOCK),
             object.optString(Constants.JIAK_FL_LAST_MODIFIED), object.optString(Constants.JIAK_FL_VTAG));
        
        JSONObject value = object.optJSONObject(Constants.JIAK_FL_VALUE);
        if (value != null) {
            this.setUsermeta(value.optJSONObject(Constants.JIAK_FL_USERMETA));
        }
    }

    public JiakObject(String bucket, String key) {
        this(bucket, key, null, null, null, null, null, null);
    }

    public JiakObject(String bucket, String key, JSONObject value) {
        this(bucket, key, value, null, null, null, null, null);
    }

    public JiakObject(String bucket, String key, JSONObject value, JSONArray links) {
        this(bucket, key, value, links, null, null, null, null);
    }

    public JiakObject(String bucket, String key, JSONObject value, JSONArray links, JSONObject usermeta) {
        this(bucket, key, value, links, usermeta, null, null, null);
    }

    public JiakObject(String bucket, String key, JSONObject value, JSONArray links, JSONObject usermeta, String vclock,
            String lastmod, String vtag) {
        this.bucket = bucket;
        this.key = key;
        if (value != null) {
            this.value = value;
        }
        if (links != null) {
            this.links = links;
        }
        if (usermeta != null) {
            this.usermeta = usermeta;
        }
        this.vclock = vclock;
        this.lastmod = lastmod;
        this.vtag = vtag;
    }

    public void copyData(RiakObject object) {
        if (object == null)
            return;

        if (object.getValue() != null) {
            try {
                value = new JSONObject(object.getValue());
            } catch (JSONException e) {
                try {
                    value = new JSONObject().put("v", JSONObject.quote(object.getValue()));
                } catch (JSONException unreached) {
                    throw new IllegalStateException("can always add quoted string to json", unreached);
                }
            }
        } else {
            value = new JSONObject();
        }
        if (object.getLinks() != null) {
            links = new JSONArray(object.getLinks());
        } else {
            links = new JSONArray();
        }
        if (object.getUsermeta() != null) {
            usermeta = new JSONObject(object.getUsermeta());
        } else {
            usermeta = new JSONObject();
        }
        vclock = object.getVclock();
        lastmod = object.getLastmod();
        vtag = object.getVtag();
    }

    public void updateMeta(StoreResponse response) {
        if (response == null)
            return;
        vclock = response.getVclock();
        lastmod = response.getLastmod();
        vtag = response.getVtag();
    }

    public String getBucket() {
        return bucket;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        if (value == null)
            return null;
        return value.toString();
    }

    public JSONObject getValueAsJSON() {
        return value;
    }

    public void setValue(String json) {
        if (json == null) {
            value = new JSONObject();
        } else {
            try {
                value = new JSONObject(json);
            } catch (JSONException e) {
                throw new IllegalArgumentException("JiakObject value must be valid JSON", e);
            }
        }
    }

    public void setValue(JSONObject object) {
        if (object == null) {
            object = new JSONObject();
        }
        value = object;
    }

    /**
     * @return the value associated with this key in this object's value (not
     *         metadata)
     */
    public Object get(String key) {
        return value.opt(key);
    }

    /**
     * @return set the value of some key in this object's value (not metadata)
     * @throws IllegalArgumentException
     *             if <code>value</code> cannot be added to valid JSON
     */
    public void set(String key, Object value) {
        if (value != null) {
            try {
                this.value.put(key, value);
            } catch (JSONException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }

    /**
     * @return An unmodifiable list of the links in this object. Use
     *         getLinksAsJSON() to add or remove links
     */
    public Collection<RiakLink> getLinks() {
        List<RiakLink> links = new ArrayList<RiakLink>();
        if (links != null && this.links.length() > 0) {
            for (int i = 0; i < this.links.length(); i++) {
                try {
                    JSONArray link = this.links.getJSONArray(i);
                    links.add(new RiakLink(link.getString(0), // bucket
                                           link.getString(1), // key
                                           link.getString(2))); // tag
                } catch (JSONException e) {}
            }
        }
        return Collections.unmodifiableList(links);
    }

    /**
     * Use this method to get the object's links and modify them. A link in Jiak
     * consists of an array with three elements: the target bucket, target key,
     * and link tag. For example, to add a link:
     * 
     * object.getLinksAsJSON().put(new String[] {"bucket", "key", "tag"});
     * 
     * @return A modifiable {@link JSONArray} representing the links of this
     *         object
     */
    public JSONArray getLinksAsJSON() {
        return links;
    }

    public void setLinks(JSONArray links) {
        if (links == null) {
            links = new JSONArray();
        }
        this.links = links;
    }

    /**
     * @return An unmodifiable map of user-defined metadata in this object. Use
     *         getUsermetaAsJSON() to modify the metadata
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> getUsermeta() {
        Map<String, String> usermeta = new HashMap<String, String>();
        for (Iterator<Object> iter = this.usermeta.keys(); iter.hasNext();) {
            String key = iter.next().toString();
            usermeta.put(key, this.usermeta.optString(key));
        }

        return Collections.unmodifiableMap(usermeta);
    }

    /**
     * Jiak does not currently support extra user-defined metadata. It only
     * stores links and the object value. Anything added to this
     * {@link JSONObject} will be added to the "usermeta" field of the value.
     */
    public JSONObject getUsermetaAsJSON() {
        return usermeta;
    }

    /**
     * Jiak does not currently support extra user-defined metadata. It only
     * stores links and the object value. Anything set here will be added to the
     * "usermeta" field of the value.
     */
    public void setUsermeta(JSONObject usermeta) {
        if (usermeta == null) {
            usermeta = new JSONObject();
        }

        this.usermeta = usermeta;
    }

    public String getContentType() {
        return Constants.CTYPE_JSON;
    }

    public String getVclock() {
        return vclock;
    }

    public String getLastmod() {
        return lastmod;
    }

    public String getVtag() {
        return vtag;
    }

    public String getEntity() {
        return this.toJSONString();
    }

    public InputStream getEntityStream() {
        if (value == null)
            return null;
        return new ByteArrayInputStream(this.getEntity().getBytes());
    }

    public long getEntityStreamLength() {
        if (value == null)
            return 0;
        return this.getEntity().getBytes().length;
    }

    public JSONObject toJSONObject() {
        JSONObject o = new JSONObject();
        try {
            JSONObject value = getValueAsJSON();
            JSONObject usermeta = getUsermetaAsJSON();
            if (usermeta != null && usermeta.keys().hasNext()) {
                if (value == null) {
                    value = new JSONObject().put(Constants.JIAK_FL_USERMETA, usermeta);
                } else if (value.opt(Constants.JIAK_FL_USERMETA) == null) {
                    value.put(Constants.JIAK_FL_USERMETA, getUsermetaAsJSON());
                }
            }
            if (value != null) {
                o.put(Constants.JIAK_FL_VALUE, getValueAsJSON());
            }
            if (getBucket() != null) {
                o.put(Constants.JIAK_FL_BUCKET, getBucket());
            }
            if (getKey() != null) {
                o.put(Constants.JIAK_FL_KEY, getKey());
            }
            if (getLinksAsJSON() != null) {
                o.put(Constants.JIAK_FL_LINKS, getLinksAsJSON());
            }
            if (getVclock() != null) {
                o.put(Constants.JIAK_FL_VCLOCK, getVclock());
            }
            if (getLastmod() != null) {
                o.put(Constants.JIAK_FL_LAST_MODIFIED, getLastmod());
            }
            if (getVtag() != null) {
                o.put(Constants.JIAK_FL_VTAG, getVtag());
            }
        } catch (JSONException e) {
            throw new IllegalStateException("can always add non null strings and json objects to json", e);
        }
        return o;
    }

    public String toJSONString() {
        return toJSONObject().toString();
    }
}
