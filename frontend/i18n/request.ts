import { getRequestConfig } from "next-intl/server";

export default getRequestConfig(async () => {
  return {
    locale: "ja",
    messages: (await import("../messages/ja.json")).default,
  };
});
