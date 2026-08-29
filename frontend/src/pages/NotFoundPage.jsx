import { Button, Container } from 'react-bootstrap';
import { Link } from 'react-router-dom';

export default function NotFoundPage() {
  return (
    <Container className="py-5 text-center">
      <h1 className="display-6">Not on the card</h1>
      <p className="text-body-secondary">That page does not exist.</p>
      <Button as={Link} to="/">
        Back to this week&apos;s games
      </Button>
    </Container>
  );
}
