import { Container } from 'react-bootstrap';

export default function SiteFooter() {
  return (
    <footer className="bg-dark text-white-50 py-4 mt-5">
      <Container className="d-flex flex-column flex-sm-row gap-2 justify-content-between align-items-center text-center text-sm-start">
        <span className="small">© {new Date().getFullYear()} Nick&apos;s Picks</span>
        <span className="small">
          Game data from CollegeFootballData.com · picks lock 30 minutes before kickoff
        </span>
      </Container>
    </footer>
  );
}
