const pulse = "animate-pulse bg-gray-200 rounded";

function Table({ rows, cols }: { rows: number; cols: number }) {
  return (
    <table className="w-full">
      <tbody>
        {Array.from({ length: rows }).map((_, r) => (
          // biome-ignore lint/suspicious/noArrayIndexKey: skeleton items have no stable key
          <tr key={r} className="border-b border-gray-100">
            {Array.from({ length: cols }).map((_, c) => (
              // biome-ignore lint/suspicious/noArrayIndexKey: skeleton items have no stable key
              <td key={c} className="px-4 py-3">
                <div className={`h-4 ${pulse}`} />
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function Text({ lines }: { lines: number }) {
  return (
    <div className="space-y-2">
      {Array.from({ length: lines }).map((_, i) => (
        <div
          // biome-ignore lint/suspicious/noArrayIndexKey: skeleton items have no stable key
          key={i}
          data-testid="skeleton-line"
          className={`h-4 ${pulse}`}
          style={{ width: i === lines - 1 ? "60%" : "100%" }}
        />
      ))}
    </div>
  );
}

function Card() {
  return (
    <div data-testid="skeleton-card" className="border rounded-lg p-4 space-y-3">
      <div className={`h-5 w-1/3 ${pulse}`} />
      <div className={`h-4 w-2/3 ${pulse}`} />
      <div className={`h-4 w-1/2 ${pulse}`} />
    </div>
  );
}

export const Skeleton = { Table, Text, Card };
