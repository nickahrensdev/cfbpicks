import { Container } from 'react-bootstrap';

export default function SiteFooter() {
  return (
    <footer className="bg-dark text-white-50 py-4 mt-5">
      <Container className="text-center text-sm-start">
        <span className="small">© {new Date().getFullYear()} Nick&apos;s Picks</span>
      </Container>
    </footer>
  );
}
