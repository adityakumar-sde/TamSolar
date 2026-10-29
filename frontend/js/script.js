"use strict";

/* =========================================================
   TAM SOLAR SOLUTIONS
   Main frontend JavaScript
   File: frontend/js/script.js
   ========================================================= */

/* =========================================================
   1. SELECT HTML ELEMENTS
   ========================================================= */

const menuToggle = document.querySelector(".menu-toggle");
const navigation = document.querySelector("#primary-nav");

const enquiryType = document.querySelector("#enquiry-type");
const form = document.querySelector("#enquiry-form");
const formStatus = document.querySelector("#form-status");

const submitButton = form?.querySelector('button[type="submit"]');

const currentYear = document.querySelector("#current-year");

const bgVideo = document.querySelector("#solar-bg-video");
const heroSection = document.querySelector("#home");

let pendingSubmissionKey = null;

/* =========================================================
   2. UPDATE COPYRIGHT YEAR
   ========================================================= */

if (currentYear) {
  currentYear.textContent = String(new Date().getFullYear());
}

/* =========================================================
   3. MOBILE NAVIGATION
   ========================================================= */

if (menuToggle && navigation) {
  menuToggle.addEventListener("click", () => {
    const isExpanded = menuToggle.getAttribute("aria-expanded") === "true";

    const shouldOpen = !isExpanded;

    menuToggle.setAttribute("aria-expanded", String(shouldOpen));

    menuToggle.setAttribute(
      "aria-label",
      shouldOpen ? "Close navigation" : "Open navigation",
    );

    navigation.classList.toggle("is-open", shouldOpen);
  });

  // Close the navigation after clicking a navigation link.
  navigation.addEventListener("click", (event) => {
    const link = event.target.closest("a");

    if (!link) return;

    navigation.classList.remove("is-open");

    menuToggle.setAttribute("aria-expanded", "false");
    menuToggle.setAttribute("aria-label", "Open navigation");
  });

  // Close the mobile navigation when Escape is pressed.
  document.addEventListener("keydown", (event) => {
    if (event.key !== "Escape") return;

    navigation.classList.remove("is-open");

    menuToggle.setAttribute("aria-expanded", "false");
    menuToggle.setAttribute("aria-label", "Open navigation");
  });
}

/* =========================================================
   4. AUTO-SELECT ENQUIRY TYPE
   ========================================================= */

// Links containing data-enquiry automatically select the
// corresponding enquiry type in the contact form.

document.querySelectorAll("[data-enquiry]").forEach((link) => {
  link.addEventListener("click", () => {
    if (!enquiryType) return;

    const requestedType = link.dataset.enquiry;

    const matchingOption = Array.from(enquiryType.options).find(
      (option) => option.value === requestedType,
    );

    if (matchingOption) {
      enquiryType.value = requestedType;
    }
  });
});

/* =========================================================
   5. FORM STATUS HELPER
   ========================================================= */

function showFormStatus(message, type = "") {
  if (!formStatus) return;

  formStatus.textContent = message;

  formStatus.classList.remove("success", "error");

  if (type === "success") {
    formStatus.classList.add("success");
  }

  if (type === "error") {
    formStatus.classList.add("error");
  }
}

/* =========================================================
   6. GENERATE SUBMISSION KEY
   ========================================================= */

// This key helps the backend identify a submission if a
// request needs to be retried.

function createSubmissionKey() {
  if (globalThis.crypto && typeof globalThis.crypto.randomUUID === "function") {
    return globalThis.crypto.randomUUID();
  }

  return `${Date.now()}-` + Math.random().toString(16).slice(2);
}

/* =========================================================
   7. ENQUIRY FORM INPUT HANDLING
   ========================================================= */

if (form && submitButton && formStatus) {
  // Generate a fresh key when the user edits the form
  // after a previous submission attempt.

  function resetPendingSubmissionKey() {
    if (!submitButton.disabled) {
      pendingSubmissionKey = null;
    }
  }

  form.addEventListener("input", resetPendingSubmissionKey);

  form.addEventListener("change", resetPendingSubmissionKey);

  /* =======================================================
     8. SUBMIT ENQUIRY TO BACKEND
     ======================================================= */

  form.addEventListener("submit", async (event) => {
    event.preventDefault();

    // Prevent duplicate clicks while submitting.
    if (submitButton.disabled) return;

    // Validate required fields using browser validation.
    if (!form.reportValidity()) return;

    // Show the sending message.
    showFormStatus("Sending your enquiry…");

    submitButton.disabled = true;
    submitButton.setAttribute("aria-busy", "true");

    // Keep the same key if this exact submission is retried.
    if (!pendingSubmissionKey) {
      pendingSubmissionKey = createSubmissionKey();
    }

    try {
      // Collect all form fields.
      const formData = new FormData(form);

      const enquiryData = Object.fromEntries(formData.entries());

      // Send the enquiry to the Spring Boot backend.
      const response = await fetch("/api/inquiries", {
        method: "POST",

        headers: {
          "Content-Type": "application/json",
          "Idempotency-Key": pendingSubmissionKey,
        },

        body: JSON.stringify(enquiryData),
      });

      // Parse the response safely.
      const result = await response.json().catch(() => ({}));

      // Handle unsuccessful HTTP responses.
      if (!response.ok) {
        throw new Error(
          result.error ||
            result.message ||
            "We couldn't send your enquiry. Please try again.",
        );
      }

      // Display the success message.
      if (result.reference) {
        showFormStatus(
          `Thank you! Your enquiry has been received. ` +
            `Reference: ${result.reference}. ` +
            `TAM Solar Solutions can follow up using the ` +
            `contact details you provided.`,
          "success",
        );
      } else {
        showFormStatus(
          "Thank you! Your enquiry has been received. " +
            "TAM Solar Solutions can follow up using the " +
            "contact details you provided.",
          "success",
        );
      }

      // Clear the form after successful submission.
      form.reset();

      // Clear the old submission key.
      pendingSubmissionKey = null;
    } catch (error) {
      console.error("TAM Solar enquiry submission failed:", error);

      const message =
        error instanceof Error
          ? error.message
          : "We couldn't send your enquiry. Please try again.";

      showFormStatus(
        `${message} You can also call +91 83830 63590 ` +
          "or email solarsolutionstam@gmail.com.",
        "error",
      );

      // Do not clear the form or pending key after a failure.
      // This allows the user to retry without losing their data.
    } finally {
      // Always restore the submit button.
      submitButton.disabled = false;

      submitButton.removeAttribute("aria-busy");
    }
  });
}

/* =========================================================
   9. HERO BACKGROUND VIDEO
   ========================================================= */

// Play the hero video while the hero section is visible.
// Pause it when the user scrolls away from the hero section.

if (bgVideo && heroSection) {
  const playVideo = () => {
    if (!bgVideo.paused) return;

    const playPromise = bgVideo.play();

    if (playPromise && typeof playPromise.catch === "function") {
      // Autoplay may be blocked by the browser.
      playPromise.catch(() => {});
    }
  };

  const pauseVideo = () => {
    if (!bgVideo.paused) return;

    // No action is needed if the video is already paused.
  };

  const stopVideo = () => {
    if (!bgVideo.paused) {
      bgVideo.pause();
    }
  };

  if ("IntersectionObserver" in window) {
    const videoObserver = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            playVideo();
          } else {
            stopVideo();
          }
        });
      },
      {
        threshold: 0.15,
      },
    );

    videoObserver.observe(heroSection);
  } else {
    // Fallback for browsers without IntersectionObserver.
    playVideo();
  }

  // Pause the video when the browser tab is hidden.
  document.addEventListener("visibilitychange", () => {
    if (document.hidden) {
      stopVideo();
    } else {
      const rect = heroSection.getBoundingClientRect();

      const heroVisible = rect.bottom > 0 && rect.top < window.innerHeight;

      if (heroVisible) {
        playVideo();
      }
    }
  });
}


/* =========================================================
   10. LEGAL MODALS (Privacy Policy & Terms and Conditions)
   ========================================================= */

const privacyModal = document.getElementById("privacy-modal");
const termsModal = document.getElementById("terms-modal");
const openPrivacyBtns = document.querySelectorAll(
  "#open-privacy-btn, #form-privacy-btn",
);
const openTermsBtns = document.querySelectorAll("#open-terms-btn");

const openLegalModal = (modal) => {
  if (!modal) return;
  modal.removeAttribute("hidden");
  document.body.style.overflow = "hidden";
  const closeBtn = modal.querySelector(".legal-modal-close");
  if (closeBtn) {
    closeBtn.focus();
  }
};

const closeLegalModal = (modal) => {
  if (!modal) return;
  modal.setAttribute("hidden", "");
  document.body.style.overflow = "";
};

openPrivacyBtns.forEach((btn) => {
  btn.addEventListener("click", (e) => {
    e.preventDefault();
    openLegalModal(privacyModal);
  });
});

openTermsBtns.forEach((btn) => {
  btn.addEventListener("click", (e) => {
    e.preventDefault();
    openLegalModal(termsModal);
  });
});

document.querySelectorAll("[data-close-modal]").forEach((btn) => {
  btn.addEventListener("click", () => {
    const parentModal = btn.closest(".legal-modal-backdrop");
    if (parentModal) {
      closeLegalModal(parentModal);
    }
  });
});

[privacyModal, termsModal].forEach((modal) => {
  if (!modal) return;
  modal.addEventListener("click", (e) => {
    if (e.target === modal) {
      closeLegalModal(modal);
    }
  });
});

window.addEventListener("keydown", (e) => {
  if (e.key === "Escape") {
    closeLegalModal(privacyModal);
    closeLegalModal(termsModal);
  }
});

/* =========================================================
   11. INITIALIZATION COMPLETE
   ========================================================= */

console.log("TAM Solar Solutions JavaScript initialized.");
