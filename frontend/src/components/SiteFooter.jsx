import { Container } from 'react-bootstrap';

import { useGroup } from '../auth/GroupProvider.jsx';

/**
 * The lock time is a group setting, so the footer states the selected group's
 * rather than a fixed 30 minutes - which stopped being true for everyone the
 * moment groups could set their own.
 */
export default function SiteFooter() {
  const { group } = useGroup();

  return (
    <footer className="bg-dark text-white-50 py-4 mt-5">
      <Container className="d-flex flex-column flex-sm-row gap-2 justify-content-between align-items-center text-center text-sm-start">
        <span className="small">© {new Date().getFullYear()} Nick&apos;s Picks</span>
        <span className="small">
          Game data from CollegeFootballData.com
          {group && ` · picks lock ${group.lockLeadMinutes} minutes before kickoff`}
        </span>
      </Container>
    </footer>
  );
}
