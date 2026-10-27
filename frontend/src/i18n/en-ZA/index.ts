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
    queue: 'Queue',
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
    demoHint: 'Demo users: thandi or pieter (customers), lerato (agent). Password: demo-password',
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
    DISPUTE_MODIFIED:
      'Someone else changed this dispute at the same moment. Reload it and try again.',
    TOO_MANY_LOGIN_ATTEMPTS: 'Too many failed sign-in attempts. Try again in a few minutes.',
    METHOD_NOT_ALLOWED: 'That action is not available here.',
    NOT_ACCEPTABLE: 'The server could not provide the information in the format requested.',
    UNSUPPORTED_MEDIA_TYPE: 'The server could not read what was sent. Please try again.',
    CLIENT_ERROR: 'That request could not be completed. Please check the details and try again.',
    INTERNAL_ERROR: 'Something went wrong on our side. Please try again.',
    NETWORK_ERROR: 'We could not reach the server. Check your connection and try again.',
    UNKNOWN_ERROR: 'Something unexpected happened. Please try again.',
  },
  common: {
    retry: 'Try again',
  },
  money: {
    debit: 'Debit',
    credit: 'Credit',
  },
  // Customer-facing wording for each dispute status (from the contract).
  disputeStatus: {
    SUBMITTED: 'Submitted',
    UNDER_REVIEW: 'In review',
    AWAITING_CUSTOMER_INFO: 'Action needed',
    RESOLVED_UPHELD: 'Resolved in your favour',
    RESOLVED_DECLINED: 'Declined',
    WITHDRAWN: 'Withdrawn',
  },
  // What the back office calls each status: "Action needed" is the customer's job, not the agent's.
  agentStatus: {
    SUBMITTED: 'Submitted',
    UNDER_REVIEW: 'In review',
    AWAITING_CUSTOMER_INFO: 'Waiting for customer',
    RESOLVED_UPHELD: 'Upheld',
    RESOLVED_DECLINED: 'Declined',
    WITHDRAWN: 'Withdrawn',
  },
  // The back office flag. Customers never see this word: they get a target date (ADR 0007).
  overdue: {
    badge: 'Overdue',
  },
  answerTarget: {
    upcoming: 'We aim to give you an answer by {date}.',
    past: "We aimed to answer by {date}. We're still working on it.",
  },
  transactionStatus: {
    PENDING: 'Pending',
    SETTLED: 'Settled',
  },
  channel: {
    CARD: 'Card',
    DEBIT_ORDER: 'Debit order',
    EFT: 'EFT',
    ATM: 'ATM',
  },
  transactions: {
    accounts: {
      label: 'Your accounts',
      availableBalance: 'Available balance',
      loading: 'Loading accounts',
      emptyTitle: 'No accounts to show',
      emptyDescription:
        'We could not find any accounts for you. If this looks wrong, please contact the bank.',
      errorTitle: 'We could not load your accounts',
    },
    filters: {
      label: 'Filter transactions',
      rangeLabel: 'Date range',
      range: {
        '30': 'Last 30 days',
        '90': 'Last 90 days',
        '120': 'Last 120 days',
        all: 'All',
      },
      search: 'Search merchant or reference',
      disputedOnly: 'Disputed only',
      clear: 'Clear filters',
    },
    list: {
      loading: 'Loading transactions',
      summary: 'Showing {from}–{to} of {total} transactions',
      pagination: 'Transaction pages',
      column: {
        date: 'Date',
        merchant: 'Merchant',
        reference: 'Reference',
        amount: 'Amount',
      },
      errorTitle: 'We could not load your transactions',
      emptyFilteredTitle: 'No transactions match these filters',
      emptyFilteredDescription:
        'Try a longer date range, a different search, or clear the filters.',
      emptyTitle: 'No transactions on this account yet',
      emptyDescription: 'New card payments, debit orders and transfers will appear here.',
    },
    badge: {
      disputed: 'Disputed',
    },
  },
  transactionDetail: {
    back: 'Back to transactions',
    loading: 'Loading transaction',
    errorTitle: 'We could not load this transaction',
    notFoundTitle: 'We could not find that transaction',
    notFoundDescription:
      'It may belong to someone else, or the link may be out of date. Go back to your transactions to find it.',
    summary: 'Transaction summary',
    fields: {
      postedAt: 'Date and time',
      reference: 'Reference',
      channel: 'Channel',
      category: 'Category',
      status: 'Status',
      type: 'Type',
    },
    eligibility: {
      heading: 'Can I dispute this?',
      eligibleTitle: 'You can dispute this transaction',
      eligibleDescription:
        'If you do not recognise this charge, or something went wrong with it, we will guide you through raising a dispute.',
      notEligibleTitle: 'This transaction cannot be disputed',
      disputeButton: 'Dispute this transaction',
      viewDispute: 'View dispute',
      existingDisputeTitle: 'This transaction has a dispute',
      // Used only if the server sends no message of its own for a refusal.
      reason: {
        NOT_A_DEBIT: 'Only money that left your account can be disputed.',
        PENDING: 'This transaction is still pending. You can dispute it once it has cleared.',
        TOO_OLD: 'This transaction is too old to dispute.',
        ALREADY_DISPUTED: 'This transaction already has an open dispute.',
      },
      noReason: 'This transaction cannot be disputed.',
    },
  },
  raiseDispute: {
    back: 'Back to transaction',
    loading: 'Loading transaction',
    errorTitle: 'We could not load this transaction',
    notFoundTitle: 'We could not find that transaction',
    notFoundDescription:
      'It may belong to someone else, or the link may be out of date. Go back to your transactions to find it.',
    notEligibleTitle: 'This transaction cannot be disputed',
    viewDispute: 'View dispute',
    backToTransactions: 'Back to transactions',
    reasonsErrorTitle: 'We could not load the dispute reasons',
    summary: 'Disputing {merchant}, {amount}, on {date}',
    steps: {
      category: {
        label: 'Category',
        title: 'What kind of problem is it?',
        hint: 'Pick the group that fits best. You will choose the exact reason next.',
      },
      reason: {
        label: 'Reason',
        title: 'What went wrong?',
        hint: 'Choose the reason that describes it best.',
        sla: 'Usually resolved within {days} day | Usually resolved within {days} days',
      },
      details: {
        label: 'Amount and details',
        title: 'How much, and what happened?',
        hint: 'Tell us the amount and, in your own words, what happened.',
      },
      review: {
        label: 'Review and submit',
        title: 'Check your dispute',
        hint: 'Make sure everything is right before you send it.',
      },
    },
    continue: 'Continue',
    previous: 'Back',
    submit: 'Submit dispute',
    fullAmount: 'You are disputing the full amount, {amount}.',
    partialToggle: 'Dispute part of this amount',
    partialAmount: 'Amount to dispute',
    partialHint: 'Up to {max}, with two decimals at most.',
    description: 'Describe what happened',
    descriptionHint: 'At least 20 characters. Include what you expected and what happened.',
    review: {
      transaction: 'Transaction',
      category: 'Category',
      reason: 'Reason',
      amount: 'Amount to dispute',
      description: 'What happened',
    },
    declaration: 'The information I have given is true and complete, to the best of my knowledge.',
    blockedTitle: {
      DISPUTE_ALREADY_EXISTS: 'This transaction already has a dispute',
      TRANSACTION_NOT_ELIGIBLE: 'This transaction cannot be disputed',
    },
    failedTitle: 'We could not submit your dispute',
    success: {
      title: 'Your dispute has been submitted',
      reference: 'Your reference number',
      referenceHint: 'Keep this number if you need to talk to us about the dispute.',
      nextHeading: 'What happens next',
      next: {
        submitted: 'Submitted: we have your dispute and it is now in our queue.',
        review: 'In review: our disputes team looks into it and may ask for more information.',
        outcome: 'Outcome: you will see the result here, and can follow every step as it happens.',
      },
      viewDispute: 'View dispute',
      backToTransactions: 'Back to transactions',
    },
  },
  dispute: {
    validation: {
      categoryRequired: 'Choose a category to continue.',
      reasonRequired: 'Choose a reason to continue.',
      amountRequired: 'Enter the amount you want to dispute.',
      amountInvalid: 'Enter an amount using digits only, for example 250.00.',
      amountDecimals: 'Use at most two decimals.',
      amountPositive: 'The amount must be more than zero.',
      amountTooHigh: 'The amount cannot be more than {max}.',
      descriptionShort: 'Describe what happened in at least 20 characters.',
      descriptionLong: 'Keep the description to 1000 characters or fewer.',
      declarationRequired: 'Please confirm the information is true before you submit.',
    },
  },
  disputes: {
    filters: {
      label: 'Filter disputes',
      statusLabel: 'Status',
      all: 'All',
      search: 'Search by reference or merchant',
      clear: 'Clear filters',
    },
    list: {
      loading: 'Loading disputes',
      summary: 'Showing {from}–{to} of {total} disputes',
      pagination: 'Dispute pages',
      column: {
        reference: 'Reference',
        merchant: 'Merchant',
        reason: 'Reason',
        amount: 'Disputed amount',
        status: 'Status',
        updated: 'Last updated',
      },
      updated: 'Updated {date}',
      errorTitle: 'We could not load your disputes',
      emptyTitle: 'You have not raised any disputes yet',
      emptyDescription:
        'If a charge looks wrong, open it from your transactions and choose "Dispute this transaction". You can follow its progress here.',
      goToTransactions: 'Go to transactions',
      emptyFilteredTitle: 'No disputes match these filters',
      emptyFilteredDescription: 'Try a different status or search, or clear the filters.',
    },
  },
  disputeDetail: {
    back: 'Back to disputes',
    loading: 'Loading dispute',
    errorTitle: 'We could not load this dispute',
    notFoundTitle: 'We could not find that dispute',
    notFoundDescription:
      'It may belong to someone else, or the link may be out of date. Go back to your disputes to find it.',
    summary: 'Dispute summary',
    fields: {
      reason: 'Reason',
      disputedAmount: 'Amount disputed',
      transactionAmount: 'Transaction amount',
      merchant: 'Merchant',
      raised: 'Raised',
      updated: 'Last updated',
      description: 'What you told us',
    },
    fullAmount: 'full amount',
    viewTransaction: 'View transaction',
    timeline: {
      heading: 'Progress',
      actor: {
        CUSTOMER: 'You',
        AGENT: 'Disputes team',
        SYSTEM: 'System',
      },
      by: 'By {actor}',
    },
    withdraw: {
      button: 'Withdraw dispute',
      title: 'Withdraw this dispute?',
      message:
        'This closes dispute {reference}. You can raise a new dispute for the same transaction afterwards.',
      keep: 'Keep dispute',
      confirm: 'Withdraw dispute',
      success: 'Dispute {reference} has been withdrawn.',
      conflict: 'This dispute can no longer be withdrawn. We have refreshed it.',
    },
  },
  reply: {
    heading: 'Reply to the disputes team',
    intro:
      'The disputes team needs more information before they can decide. Your reply is added to the dispute.',
    asked: 'They asked',
    label: 'Your reply',
    hint: 'Up to 1000 characters.',
    submit: 'Send reply',
    success: 'Your reply has been sent to the disputes team.',
    conflict: {
      INVALID_DISPUTE_TRANSITION:
        'This dispute is no longer waiting for your reply. We have refreshed it.',
      DISPUTE_MODIFIED:
        'This dispute changed while you were writing. We have refreshed it, so please check it and send your reply again.',
    },
    banner: {
      title: 'Action needed: the disputes team asked you for more information.',
      action: 'Reply now',
    },
    validation: {
      required: 'Write a reply before you send it.',
      tooLong: 'Keep your reply to 1000 characters or fewer.',
    },
  },
  agentQueue: {
    tabsLabel: 'Queue',
    tabs: {
      UNASSIGNED: 'Unassigned',
      MINE: 'Mine',
      ALL: 'All',
    },
    search: 'Search reference, merchant or customer',
    sort: {
      label: 'Sort by',
      DUE_AT: 'Due soonest',
      CREATED_AT: 'Oldest first',
    },
    overdueOnly: 'Overdue only',
    loading: 'Loading the queue',
    summary: 'Showing {from}–{to} of {total} disputes',
    pagination: 'Queue pages',
    column: {
      reference: 'Reference',
      customer: 'Customer',
      merchant: 'Merchant',
      reason: 'Reason',
      amount: 'Amount',
      status: 'Status',
      due: 'Due',
      assignedTo: 'Assigned to',
      created: 'Raised',
    },
    unassigned: 'Unassigned',
    assignedTo: 'Assigned to {name}',
    created: 'Raised {date}',
    due: 'Due {date}',
    errorTitle: 'We could not load the queue',
    empty: {
      UNASSIGNED: {
        title: 'Nothing is waiting for an agent',
        description:
          'Every open dispute has an owner. New disputes appear here as customers raise them.',
      },
      MINE: {
        title: 'You have no disputes assigned',
        description: 'Claim a dispute from the Unassigned tab and it will be listed here.',
      },
      ALL: {
        title: 'There are no disputes yet',
        description: 'Disputes that customers raise will appear here.',
      },
    },
    emptyFilteredTitle: 'No disputes match your search',
    emptyFilteredDescription: 'Check the spelling, or clear the search to see the whole tab.',
    emptyFilteredOverdueDescription:
      'Check the spelling, or clear the search. Only overdue disputes are being shown.',
    emptyOverdueTitle: 'Nothing overdue. Nice work.',
    emptyOverdueDescription:
      'Every open dispute here is still within its due date. Switch the filter off to see them all.',
    showAll: 'Show all open disputes',
    clearSearch: 'Clear search',
    goToUnassigned: 'Go to Unassigned',
  },
  agentDispute: {
    back: 'Back to the queue',
    loading: 'Loading dispute',
    errorTitle: 'We could not load this dispute',
    notFoundTitle: 'We could not find that dispute',
    notFoundDescription: 'The link may be out of date. Go back to the queue to find it.',
    summary: 'Dispute summary',
    unassigned: 'Unassigned',
    fields: {
      reason: 'Reason',
      customer: 'Customer',
      merchant: 'Merchant',
      disputedAmount: 'Amount disputed',
      assignedAgent: 'Assigned agent',
      transaction: 'Transaction',
      raised: 'Raised',
      due: 'Due',
      updated: 'Last updated',
      description: 'Customer description',
    },
    transactionValue: '{reference}, posted {date}',
    timeline: {
      heading: 'History',
      by: 'By {actor}',
      actor: {
        CUSTOMER: 'Customer',
        AGENT: 'Agent',
        SYSTEM: 'System',
      },
      internalNote: 'Internal note',
      internalNoteHint: 'Only the back office can see this.',
    },
    actions: {
      label: 'Actions for this dispute',
      claim: 'Claim',
      requestInfo: 'Request information',
      resolve: 'Resolve',
      none: 'You have no actions on this dispute right now.',
    },
    claim: {
      success: 'You now own {reference}. It is in review.',
    },
    requestInfo: {
      title: 'Ask the customer for more information',
      description: 'The customer sees this in their timeline and is asked to reply.',
      message: 'Message to the customer',
      hint: 'Up to 1000 characters.',
      submit: 'Send request',
      cancel: 'Cancel',
      success: 'Your request for {reference} has been sent to the customer.',
    },
    resolve: {
      title: 'Resolve this dispute',
      outcome: 'Outcome',
      upheld: 'Upheld: the customer is right',
      declined: 'Declined: the charge stands',
      customerMessage: 'Message to the customer',
      customerMessageHint: 'The customer sees this in their timeline.',
      internalNote: 'Internal note',
      internalNoteHint: 'Only the back office can see this.',
      submit: 'Resolve dispute',
      cancel: 'Cancel',
      success: {
        UPHELD: '{reference} resolved as upheld.',
        DECLINED: '{reference} resolved as declined.',
      },
    },
    conflict: {
      INVALID_DISPUTE_TRANSITION:
        'That action is no longer available on this dispute. We have refreshed it.',
      DISPUTE_MODIFIED:
        'Someone else changed this dispute at the same moment. We have refreshed it, so please check it and try again.',
    },
    validation: {
      messageRequired: 'Write a message for the customer.',
      customerMessageRequired: 'Write a message for the customer.',
      internalNoteRequired: 'Write an internal note.',
      outcomeRequired: 'Choose an outcome.',
      tooLong: 'Keep this to 1000 characters or fewer.',
    },
  },
  pages: {
    transactions: {
      title: 'Transactions',
    },
    transactionDetail: {
      title: 'Transaction details',
    },
    disputeNew: {
      title: 'Raise a dispute',
    },
    disputes: {
      title: 'Your disputes',
    },
    disputeDetail: {
      title: 'Dispute details',
    },
    agentQueue: {
      title: 'Dispute queue',
    },
    agentDispute: {
      title: 'Review dispute',
    },
    notFound: {
      title: 'Page not found',
      emptyTitle: 'We could not find that page',
      emptyDescription: 'The link may be out of date. Head back to your transactions to carry on.',
      action: 'Go to transactions',
      agentDescription: 'The link may be out of date. Head back to the queue to carry on.',
      agentAction: 'Go to the queue',
    },
  },
};
