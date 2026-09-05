import { useCallback, useEffect, useState } from "react";

export function useAsync<T>(fn: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<Error | null>(null);
  const [loading, setLoading] = useState(true);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const memo = useCallback(fn, deps);

  const run = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setData(await memo());
    } catch (e) {
      setError(e as Error);
    } finally {
      setLoading(false);
    }
  }, [memo]);

  useEffect(() => {
    run();
  }, [run]);

  return { data, error, loading, reload: run };
}
