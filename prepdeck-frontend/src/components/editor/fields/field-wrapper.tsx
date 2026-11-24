import type { ReactNode } from 'react';

interface FieldWrapperProps {
  children: ReactNode;
  columns?: number;
}

export function FieldWrapper({ children, columns = 2 }: FieldWrapperProps) {
  return (
    <div className="grid gap-2.5" style={{ gridTemplateColumns: `repeat(${columns}, 1fr)` }}>
      {children}
    </div>
  );
}
