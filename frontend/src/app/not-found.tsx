import Link from 'next/link';

export default function NotFound() {
  return (
    <div className="container-page py-24 text-center">
      <h1 className="text-3xl">Page not found</h1>
      <Link href="/" className="btn-primary mt-6">Go home</Link>
    </div>
  );
}
