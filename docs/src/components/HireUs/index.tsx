import styles from './styles.module.css';

const HIRE_US_URL =
  'https://swmansion.com/contact/?utm_source=react-native-bottom-sheet-docs&utm_medium=sidebar';

export default function HireUs() {
  return (
    <aside className={styles.hireUs}>
      <p className={styles.copy}>We are Software Mansion.</p>
      <a
        className={styles.cta}
        href={HIRE_US_URL}
        target="_blank"
        rel="noopener noreferrer"
      >
        Hire us
        <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden>
          <path fill="currentColor" d="M16.01 11H4v2h12.01v3L20 12l-3.99-4z" />
        </svg>
      </a>
    </aside>
  );
}
