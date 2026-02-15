"use client";

/**
 * ProjectSelector Component
 *
 * Searchable dropdown for selecting a project from the user's assigned projects.
 * Supports filtering by project code or name with keyboard navigation.
 *
 * Feature: 003-project-selector-worklog
 */

import { useCallback, useEffect, useId, useRef, useState } from "react";
import { api } from "@/services/api";
import type { AssignedProject } from "@/types/worklog";

interface ProjectSelectorProps {
  /** ID of the member to fetch projects for */
  memberId: string;
  /** Currently selected project ID */
  value: string;
  /** Callback when a project is selected */
  onChange: (projectId: string) => void;
  /** Whether the selector is disabled */
  disabled?: boolean;
  /** Error message to display */
  error?: string;
  /** Optional CSS class */
  className?: string;
  /** Placeholder text when no project is selected */
  placeholder?: string;
  /** Optional ID for the input (for label association) */
  id?: string;
}

export function ProjectSelector({
  memberId,
  value,
  onChange,
  disabled = false,
  error,
  className = "",
  placeholder = "Select a project...",
  id,
}: ProjectSelectorProps) {
  const generatedInputId = useId();
  const inputId = id || generatedInputId;
  const listboxId = useId();
  const containerRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const [projects, setProjects] = useState<AssignedProject[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [isOpen, setIsOpen] = useState(false);
  const [searchText, setSearchText] = useState("");
  const [highlightedIndex, setHighlightedIndex] = useState(-1);

  // Get the currently selected project for display
  const selectedProject = projects.find((p) => p.id === value);

  // Filter projects based on search text
  const filteredProjects = projects.filter((project) => {
    if (!searchText) return true;
    const search = searchText.toLowerCase();
    return project.code.toLowerCase().includes(search) || project.name.toLowerCase().includes(search);
  });

  // Fetch projects on mount
  useEffect(() => {
    async function fetchProjects() {
      if (!memberId) return;

      setIsLoading(true);
      setLoadError(null);

      try {
        const response = await api.members.getAssignedProjects(memberId);
        setProjects(response.projects);
      } catch (err) {
        setLoadError(err instanceof Error ? err.message : "Failed to load projects");
        setProjects([]);
      } finally {
        setIsLoading(false);
      }
    }

    fetchProjects();
  }, [memberId]);

  // Handle click outside to close dropdown
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false);
        // Reset search text to show selected value
        if (selectedProject) {
          setSearchText("");
        }
      }
    }

    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [selectedProject]);

  // Handle project selection
  // Note: setSearchText, setIsOpen, setHighlightedIndex are React state setters
  // which are guaranteed to be stable and don't need to be in the dependency array
  const handleSelect = useCallback(
    (project: AssignedProject) => {
      onChange(project.id);
      setSearchText("");
      setIsOpen(false);
      setHighlightedIndex(-1);
    },
    [onChange],
  );

  // Handle keyboard navigation
  const handleKeyDown = useCallback(
    (event: React.KeyboardEvent) => {
      if (disabled) return;

      switch (event.key) {
        case "ArrowDown":
          event.preventDefault();
          if (!isOpen) {
            setIsOpen(true);
            setHighlightedIndex(0);
          } else {
            setHighlightedIndex((prev) => (prev < filteredProjects.length - 1 ? prev + 1 : prev));
          }
          break;
        case "ArrowUp":
          event.preventDefault();
          setHighlightedIndex((prev) => (prev > 0 ? prev - 1 : 0));
          break;
        case "Enter":
          event.preventDefault();
          if (isOpen && highlightedIndex >= 0 && filteredProjects[highlightedIndex]) {
            handleSelect(filteredProjects[highlightedIndex]);
          } else if (!isOpen) {
            setIsOpen(true);
          }
          break;
        case "Escape":
          event.preventDefault();
          setIsOpen(false);
          setSearchText("");
          break;
        case "Tab":
          setIsOpen(false);
          break;
      }
    },
    [disabled, isOpen, highlightedIndex, filteredProjects, handleSelect],
  );

  // Handle input change for search
  const handleInputChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const newValue = event.target.value;
    setSearchText(newValue);
    setIsOpen(true);
    setHighlightedIndex(0);
  };

  // Handle input focus
  const handleFocus = () => {
    if (!disabled) {
      setIsOpen(true);
    }
  };

  // Get display value for input
  const getDisplayValue = () => {
    if (isOpen && searchText) {
      return searchText;
    }
    if (selectedProject) {
      return `${selectedProject.code} - ${selectedProject.name}`;
    }
    return "";
  };

  // Render loading state
  if (isLoading) {
    return (
      <div className={`relative ${className}`}>
        <input
          type="text"
          disabled
          placeholder="Loading projects..."
          className="w-full px-3 py-2 border rounded-md bg-gray-100 cursor-not-allowed"
        />
      </div>
    );
  }

  // Render error state
  if (loadError) {
    return (
      <div className={`relative ${className}`}>
        <div className="text-sm text-red-600 bg-red-50 p-2 rounded border border-red-200">{loadError}</div>
      </div>
    );
  }

  // Render empty state (no projects assigned)
  if (projects.length === 0) {
    return (
      <div className={`relative ${className}`}>
        <div className="text-sm text-amber-700 bg-amber-50 p-2 rounded border border-amber-200">
          No projects assigned. Please contact your administrator.
        </div>
      </div>
    );
  }

  return (
    <div ref={containerRef} className={`relative ${className}`}>
      {/* Combobox Input */}
      <div className="relative">
        <input
          ref={inputRef}
          id={inputId}
          type="text"
          role="combobox"
          aria-expanded={isOpen}
          aria-controls={listboxId}
          aria-autocomplete="list"
          aria-activedescendant={highlightedIndex >= 0 ? `project-option-${highlightedIndex}` : undefined}
          value={getDisplayValue()}
          onChange={handleInputChange}
          onFocus={handleFocus}
          onKeyDown={handleKeyDown}
          disabled={disabled}
          placeholder={placeholder}
          className={`w-full px-3 py-2 pr-8 border rounded-md 
            ${disabled ? "bg-gray-100 cursor-not-allowed" : "bg-white cursor-text"}
            ${error ? "border-red-500" : "border-gray-300"}
            focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500`}
        />
        {/* Dropdown Arrow */}
        <button
          type="button"
          tabIndex={-1}
          onClick={() => !disabled && setIsOpen(!isOpen)}
          disabled={disabled}
          aria-label="Toggle project list"
          className={`absolute right-2 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600
            ${disabled ? "cursor-not-allowed" : "cursor-pointer"}`}
        >
          <svg
            className={`w-4 h-4 transition-transform ${isOpen ? "rotate-180" : ""}`}
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
            aria-hidden="true"
          >
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
          </svg>
        </button>
      </div>

      {/* Error Message */}
      {error && <div className="text-red-600 text-sm mt-1">{error}</div>}

      {/* Dropdown List */}
      {isOpen && (
        <div
          id={listboxId}
          role="listbox"
          aria-label="Projects"
          className="absolute z-50 w-full mt-1 max-h-60 overflow-auto bg-white border border-gray-300 rounded-md shadow-lg"
        >
          {filteredProjects.length === 0 ? (
            <div className="px-3 py-2 text-gray-500 text-sm">No matching projects found</div>
          ) : (
            filteredProjects.map((project, index) => (
              <div
                key={project.id}
                id={`project-option-${index}`}
                role="option"
                tabIndex={-1}
                aria-selected={project.id === value}
                onClick={() => handleSelect(project)}
                onKeyDown={(e) => {
                  if (e.key === "Enter" || e.key === " ") {
                    e.preventDefault();
                    handleSelect(project);
                  }
                }}
                onMouseEnter={() => setHighlightedIndex(index)}
                className={`px-3 py-2 cursor-pointer text-sm
                  ${index === highlightedIndex ? "bg-blue-100" : ""}
                  ${project.id === value ? "bg-blue-50 font-medium" : ""}
                  hover:bg-blue-100`}
              >
                <span className="font-mono text-blue-600">{project.code}</span>
                <span className="mx-2">-</span>
                <span>{project.name}</span>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
}
