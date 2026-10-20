package io.github.coreplatform.common.web;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;

/**
 * Envelope for collections.
 *
 * @param object always "list"
 * @param url request URL
 * @param data page items
 * @param pageInfo cursor metadata
 */
@JsonPropertyOrder({"object", "url", "data", "pageInfo"})
public record ListEnvelope<T>(
        String object,
        String url,
        List<T> data,
        PageInfo pageInfo) {

    public static <T> ListEnvelope<T> of(String url, List<T> data, boolean hasNextPage, String nextCursor) {
        return new ListEnvelope<>("list", url, data, new PageInfo(hasNextPage, nextCursor));
    }

    public record PageInfo(boolean hasNextPage, String nextCursor) {}
}
