const menuToggle = document.querySelector(".menu-toggle");
const navigation = document.querySelector("#primary-nav");
const enquiryType = document.querySelector("#enquiry-type");
const form = document.querySelector("#enquiry-form");
const formStatus = document.querySelector("#form-status");
const submitButton = form.querySelector('button[type="submit"]');
let pendingSubmissionKey = null;

document.querySelector("#current-year").textContent = String(new Date().getFullYear());

menuToggle.addEventListener("click", () => {
  const isExpanded = menuToggle.getAttribute("aria-expanded") === "true";
  menuToggle.setAttribute("aria-expanded", String(!isExpanded));
  menuToggle.setAttribute("aria-label", isExpanded ? "Open navigation" : "Close navigation");
  navigation.classList.toggle("is-open", !isExpanded);
});

navigation.addEventListener("click", (event) => {
  if (event.target.closest("a")) {
    navigation.classList.remove("is-open");
    menuToggle.setAttribute("aria-expanded", "false");
    menuToggle.setAttribute("aria-label", "Open navigation");
  }
});

document.querySelectorAll("[data-enquiry]").forEach((link) => {
  link.addEventListener("click", () => {
    enquiryType.value = link.dataset.enquiry;
  });
});

form.addEventListener("input", () => {
  if (!submitButton.disabled) pendingSubmissionKey = null;
});

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  if (submitButton.disabled || !form.reportValidity()) return;

  formStatus.textContent = "";
  submitButton.disabled = true;
  submitButton.setAttribute("aria-busy", "true");
  formStatus.textContent = "Sending your enquiry…";
  pendingSubmissionKey ||= globalThis.crypto?.randomUUID?.()
    || `${Date.now()}-${Math.random().toString(16).slice(2)}`;

  try {
    const response = await fetch("/api/inquiries", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Idempotency-Key": pendingSubmissionKey,
      },
      body: JSON.stringify(Object.fromEntries(new FormData(form))),
    });
    const result = await response.json().catch(() => ({}));
    if (!response.ok) {
      throw new Error(result.error || "We couldn’t send your enquiry. Please try again.");
    }
    formStatus.textContent = `Thanks — your enquiry has been received. Your reference is ${result.reference}. TAM Solar Solutions can follow up using the contact details you provided.`;
    form.reset();
    pendingSubmissionKey = null;
  } catch (error) {
    const message = error instanceof Error
      ? error.message
      : "We couldn’t send your enquiry. Please try again.";
    formStatus.textContent = `${message} You can also call +91 83830 63590 or email solarsolutionstam@gmail.com.`;
  } finally {
    submitButton.disabled = false;
    submitButton.removeAttribute("aria-busy");
  }
});

// Background video auto-play on scroll
const bgVideo = document.getElementById('solar-bg-video');
const heroSection = document.getElementById('home');
if (bgVideo && heroSection) {
  const observer = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) {
        bgVideo.play().catch(() => {});
      } else {
        bgVideo.pause();
      }
    });
  }, { threshold: 0.15 });
  observer.observe(heroSection);

  window.addEventListener('scroll', () => {
    const rect = heroSection.getBoundingClientRect();
    if (rect.bottom > 50 && rect.top < window.innerHeight) {
      if (bgVideo.paused) bgVideo.play().catch(() => {});
    }
  }, { passive: true });
}
