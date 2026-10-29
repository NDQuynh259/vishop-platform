package io.github.coreplatform.common.cache.redis;

/** Shared Lua protocol: persistent revision watermark and server-side freshness timestamp. */
public final class VersionedAuthorizationCache {
  private VersionedAuthorizationCache() {}

  public static final String WRITE =
      """
      local previous = redis.call('HGET', KEYS[1], 'version')
      local next = ARGV[1]
      if not previous or #next > #previous or (#next == #previous and next > previous) then
        redis.call('HSET', KEYS[1], 'version', next, 'payload', ARGV[2])
      elseif next ~= previous then
        return 0
      end
      local now = redis.call('TIME')
      redis.call('HSET', KEYS[1], 'checkedAt', now[1])
      return 1
      """;
  public static final String READ =
      """
      local checked = redis.call('HGET', KEYS[1], 'checkedAt')
      local now = redis.call('TIME')
      if not checked or tonumber(now[1]) < tonumber(checked) or tonumber(now[1]) - tonumber(checked) >= tonumber(ARGV[1]) then return nil end
      return redis.call('HGET', KEYS[1], 'payload')
      """;
}
