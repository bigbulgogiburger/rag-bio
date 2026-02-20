import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import AppErrorBoundary from "./AppErrorBoundary";
import PageErrorBoundary from "./PageErrorBoundary";
import TabErrorBoundary from "./TabErrorBoundary";

// Suppress console.error from error boundaries during tests
beforeEach(() => {
  vi.spyOn(console, "error").mockImplementation(() => {});
});

// A component that throws on render
function ThrowingChild({ message }: { message: string }) {
  throw new Error(message);
}

function GoodChild() {
  return <div>정상 렌더링</div>;
}

describe("AppErrorBoundary", () => {
  it("renders children when no error occurs", () => {
    render(
      <AppErrorBoundary>
        <GoodChild />
      </AppErrorBoundary>,
    );
    expect(screen.getByText("정상 렌더링")).toBeInTheDocument();
  });

  it("shows app-level fallback when child throws", () => {
    render(
      <AppErrorBoundary>
        <ThrowingChild message="앱 에러 발생" />
      </AppErrorBoundary>,
    );
    expect(screen.getByRole("alert")).toBeInTheDocument();
    expect(screen.getByText("애플리케이션 오류가 발생했습니다")).toBeInTheDocument();
    expect(screen.getByText("다시 시도")).toBeInTheDocument();
    expect(screen.getByText("페이지 새로고침")).toBeInTheDocument();
  });

  it("resets error state when '다시 시도' is clicked", () => {
    let shouldThrow = true;
    function ConditionalThrow() {
      if (shouldThrow) throw new Error("조건부 에러");
      return <div>복구됨</div>;
    }

    render(
      <AppErrorBoundary>
        <ConditionalThrow />
      </AppErrorBoundary>,
    );

    expect(screen.getByText("애플리케이션 오류가 발생했습니다")).toBeInTheDocument();

    shouldThrow = false;
    fireEvent.click(screen.getByText("다시 시도"));

    expect(screen.getByText("복구됨")).toBeInTheDocument();
  });
});

describe("PageErrorBoundary", () => {
  it("renders children when no error occurs", () => {
    render(
      <PageErrorBoundary>
        <GoodChild />
      </PageErrorBoundary>,
    );
    expect(screen.getByText("정상 렌더링")).toBeInTheDocument();
  });

  it("shows page-level fallback when child throws", () => {
    render(
      <PageErrorBoundary>
        <ThrowingChild message="페이지 에러" />
      </PageErrorBoundary>,
    );
    expect(screen.getByRole("alert")).toBeInTheDocument();
    expect(screen.getByText("페이지를 불러올 수 없습니다")).toBeInTheDocument();
    expect(screen.getByText("다시 시도")).toBeInTheDocument();
    // Page level should NOT have "페이지 새로고침" button (only app level has it)
    expect(screen.queryByText("페이지 새로고침")).not.toBeInTheDocument();
  });

  it("resets error state when '다시 시도' is clicked", () => {
    let shouldThrow = true;
    function ConditionalThrow() {
      if (shouldThrow) throw new Error("페이지 에러");
      return <div>페이지 복구됨</div>;
    }

    render(
      <PageErrorBoundary>
        <ConditionalThrow />
      </PageErrorBoundary>,
    );

    shouldThrow = false;
    fireEvent.click(screen.getByText("다시 시도"));
    expect(screen.getByText("페이지 복구됨")).toBeInTheDocument();
  });
});

describe("TabErrorBoundary", () => {
  it("renders children when no error occurs", () => {
    render(
      <TabErrorBoundary>
        <GoodChild />
      </TabErrorBoundary>,
    );
    expect(screen.getByText("정상 렌더링")).toBeInTheDocument();
  });

  it("shows tab-level fallback when child throws", () => {
    render(
      <TabErrorBoundary>
        <ThrowingChild message="탭 에러" />
      </TabErrorBoundary>,
    );
    expect(screen.getByRole("alert")).toBeInTheDocument();
    expect(screen.getByText("콘텐츠를 표시할 수 없습니다")).toBeInTheDocument();
    expect(screen.getByText("다시 시도")).toBeInTheDocument();
    expect(screen.queryByText("페이지 새로고침")).not.toBeInTheDocument();
  });

  it("resets error state when '다시 시도' is clicked", () => {
    let shouldThrow = true;
    function ConditionalThrow() {
      if (shouldThrow) throw new Error("탭 에러");
      return <div>탭 복구됨</div>;
    }

    render(
      <TabErrorBoundary>
        <ConditionalThrow />
      </TabErrorBoundary>,
    );

    shouldThrow = false;
    fireEvent.click(screen.getByText("다시 시도"));
    expect(screen.getByText("탭 복구됨")).toBeInTheDocument();
  });
});
