import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import Tabs from "../Tabs";

const tabs = [
  { key: "tab1", label: "탭 1", content: <div>탭 1 내용</div> },
  { key: "tab2", label: "탭 2", content: <div>탭 2 내용</div> },
  { key: "tab3", label: "탭 3", content: <div>탭 3 내용</div> },
];

describe("Tabs", () => {
  it("renders all tab triggers", () => {
    render(<Tabs tabs={tabs} />);
    expect(screen.getByRole("tab", { name: "탭 1" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "탭 2" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "탭 3" })).toBeInTheDocument();
  });

  it("shows first tab content by default", () => {
    render(<Tabs tabs={tabs} />);
    expect(screen.getByText("탭 1 내용")).toBeInTheDocument();
  });

  it("shows specified defaultTab content", () => {
    render(<Tabs tabs={tabs} defaultTab="tab2" />);
    expect(screen.getByText("탭 2 내용")).toBeInTheDocument();
  });

  it("switches tab content on click", async () => {
    const user = userEvent.setup();
    render(<Tabs tabs={tabs} />);

    await user.click(screen.getByRole("tab", { name: "탭 2" }));
    expect(screen.getByText("탭 2 내용")).toBeInTheDocument();
  });

  it("renders tablist with aria-label", () => {
    render(<Tabs tabs={tabs} />);
    expect(screen.getByRole("tablist")).toHaveAttribute("aria-label", "탭 목록");
  });
});
