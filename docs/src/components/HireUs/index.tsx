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
        <svg
          width="24"
          height="24"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden
        >
          <path d="M5 12h14M12 5l7 7-7 7" />
        </svg>
      </a>
    </aside>
  );
}
