import { useCallback, useRef, useState } from 'react';

interface UseSSEReturn {
  data: string;
  loading: boolean;
  error: string | null;
  start: (url: string) => void;
  stop: () => void;
}

export function useSSE(): UseSSEReturn {
  const [data, setData] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const sourceRef = useRef<EventSource | null>(null);

  const stop = useCallback(() => {
    if (sourceRef.current) {
      sourceRef.current.close();
      sourceRef.current = null;
    }
    setLoading(false);
  }, []);

  const start = useCallback((url: string) => {
    stop();
    setData('');
    setError(null);
    setLoading(true);

    const source = new EventSource(url);
    sourceRef.current = source;

    source.onmessage = (event) => {
      setData((prev) => prev + event.data);
    };

    source.onerror = () => {
      // SSE 正常结束也会触发 onerror，检查 readyState
      if (source.readyState === EventSource.CLOSED) {
        setLoading(false);
      } else {
        setError('连接中断');
        setLoading(false);
      }
      source.close();
      sourceRef.current = null;
    };
  }, [stop]);

  return { data, loading, error, start, stop };
}
