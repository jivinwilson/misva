export const INDUSTRIES = [
  { code: "restaurant", label: "Restaurant" },
  { code: "hotel", label: "Hotel / Resort" },
  { code: "salon", label: "Salon / Spa" },
  { code: "retail", label: "Retail" },
  { code: "cafe", label: "Café / Bakery" },
  { code: "gym", label: "Gym / Fitness" },
  { code: "clinic", label: "Clinic" },
] as const;

export const COUNTRIES = [
  { code: "IN", name: "India", currency: "INR", timezone: "Asia/Kolkata" },
  { code: "AE", name: "United Arab Emirates", currency: "AED", timezone: "Asia/Dubai" },
  { code: "GB", name: "United Kingdom", currency: "GBP", timezone: "Europe/London" },
  { code: "US", name: "United States", currency: "USD", timezone: "America/New_York" },
  { code: "CA", name: "Canada", currency: "CAD", timezone: "America/Toronto" },
  { code: "AU", name: "Australia", currency: "AUD", timezone: "Australia/Sydney" },
  { code: "SG", name: "Singapore", currency: "SGD", timezone: "Asia/Singapore" },
] as const;

export const industryLabel = (code: string) => INDUSTRIES.find((i) => i.code === code)?.label ?? code;
