import { useCallback, useEffect, useRef, useState } from "react";

export function useAsync<T>(fn: () => Promise<T>, deps: any[] = []) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<Error | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const mounted = useRef(true);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const memoFn = useCallback(fn, deps);

  const run = useCallback(
    async (isRefresh: boolean) => {
      isRefresh ? setRefreshing(true) : setLoading(true);
      setError(null);
      try {
        const res = await memoFn();
        if (mounted.current) setData(res);
      } catch (e: any) {
        if (mounted.current) setError(e);
      } finally {
        if (mounted.current) {
          setLoading(false);
          setRefreshing(false);
        }
      }
    },
    [memoFn]
  );

  useEffect(() => {
    mounted.current = true;
    run(false);
    return () => {
      mounted.current = false;
    };
  }, [run]);

  return { data, error, loading, refreshing, reload: () => run(false), refresh: () => run(true), setData };
}
