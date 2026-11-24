import type { CSSProperties } from 'react';

interface AvatarImageProps {
  src?: string;
  avatarStyle?: 'circle' | 'oneInch';
  size?: number;
  className?: string;
}

export function AvatarImage({ src, avatarStyle = 'oneInch', size = 64, className }: AvatarImageProps) {
  if (!src) return null;
  const style: CSSProperties = {
    width: size,
    height: size,
    objectFit: 'cover',
    borderRadius: avatarStyle === 'circle' ? '50%' : 6,
    display: 'block',
    border: avatarStyle === 'circle' ? '2px solid #fff' : '1px solid rgba(0,0,0,0.12)',
  };
  return <img src={src} alt="avatar" style={style} className={className} />;
}
