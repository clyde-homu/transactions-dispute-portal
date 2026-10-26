// Every user-facing string lives here. Keys are grouped by screen so the next feature
// phase can add its own block without touching the shared ones.
export default {
  app: {
    name: 'Dispute Portal',
    skipToContent: 'Skip to main content',
  },
  nav: {
    primary: 'Main navigation',
    transactions: 'Transactions',
    disputes: 'Disputes',
    signOut: 'Sign out',
    signedInAs: 'Signed in as {name}',
  },
  auth: {
    sessionExpired: 'Your session has ended. Please sign in again.',
    signOutFailed:
      'We could not reach the server to end your session. You have been signed out on this device.',
  },
  login: {
    title: 'Sign in',
    subtitle: 'View your transactions and follow your disputes.',
    username: 'Username',
    password: 'Password',
    showPassword: 'Show password',
    hidePassword: 'Hide password',
    submit: 'Sign in',
    demoHint: 'Demo users: thandi or pieter, password demo-password',
    validation: {
      usernameRequired: 'Enter your username.',
      usernameTooLong: 'Your username can be at most 50 characters.',
      passwordRequired: 'Enter your password.',
      passwordTooLong: 'Your password can be at most 72 characters.',
    },
  },
  // Keyed by the stable problem code from the API; the server text is only a fallback.
  errors: {
    VALIDATION_FAILED: 'Some of the details need your attention.',
    BAD_CREDENTIALS: 'The username or password is incorrect.',
    UNAUTHENTICATED: 'Your session has ended. Please sign in again.',
    FORBIDDEN: 'You do not have permission to do that.',
    NOT_FOUND: 'We could not find what you were looking for.',
    TRANSACTION_NOT_ELIGIBLE: 'This transaction cannot be disputed.',
    DISPUTE_ALREADY_EXISTS: 'A dispute already exists for this transaction.',
    INVALID_DISPUTE_TRANSITION: 'This dispute can no longer be changed that way.',
    TOO_MANY_LOGIN_ATTEMPTS: 'Too many failed sign-in attempts. Try again in a few minutes.',
    METHOD_NOT_ALLOWED: 'That action is not available here.',
    NOT_ACCEPTABLE: 'The server could not provide the information in the format requested.',
    UNSUPPORTED_MEDIA_TYPE: 'The server could not read what was sent. Please try again.',
    CLIENT_ERROR: 'That request could not be completed. Please check the details and try again.',
    INTERNAL_ERROR: 'Something went wrong on our side. Please try again.',
    NETWORK_ERROR: 'We could not reach the server. Check your connection and try again.',
    UNKNOWN_ERROR: 'Something unexpected happened. Please try again.',
  },
  money: {
    debit: 'Debit',
    credit: 'Credit',
  },
  pages: {
    transactions: {
      title: 'Transactions',
      emptyTitle: 'Your transactions will appear here',
      emptyDescription:
        'Pick an account to see its recent activity. You will be able to filter, search and open a transaction to dispute it.',
    },
    transactionDetail: {
      title: 'Transaction details',
      emptyTitle: 'Transaction details are on their way',
      emptyDescription:
        'This page will show the full details of a transaction and whether you can dispute it.',
    },
    disputeNew: {
      title: 'Raise a dispute',
      emptyTitle: 'Disputes are coming soon',
      emptyDescription:
        'You will be guided step by step: choose a reason, confirm the amount and describe what happened.',
    },
    disputes: {
      title: 'Your disputes',
      emptyTitle: 'No disputes to show yet',
      emptyDescription:
        'When you dispute a transaction, you can follow its progress here. Start from the Transactions tab.',
    },
    disputeDetail: {
      title: 'Dispute details',
      emptyTitle: 'Dispute details are on their way',
      emptyDescription: 'This page will show where your dispute stands and what happens next.',
    },
    notFound: {
      title: 'Page not found',
      emptyTitle: 'We could not find that page',
      emptyDescription: 'The link may be out of date. Head back to your transactions to carry on.',
      action: 'Go to transactions',
    },
  },
};
