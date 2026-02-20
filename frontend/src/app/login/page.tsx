"use client";

import LoginForm from "@/components/auth/LoginForm";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  CardDescription,
} from "@/components/ui/card";

export default function LoginPage() {
  return (
    <div className="flex min-h-[60vh] items-center justify-center">
      <Card className="w-full max-w-md">
        <CardHeader className="text-center">
          <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-xl bg-gradient-to-br from-blue-500 to-blue-600 shadow-sm">
            <svg
              width="24"
              height="24"
              viewBox="0 0 18 18"
              fill="none"
              xmlns="http://www.w3.org/2000/svg"
              aria-hidden="true"
            >
              <path
                d="M3 4.5C3 4.5 5 3 9 3C13 3 15 4.5 15 4.5V14.5C15 14.5 13 13 9 13C5 13 3 14.5 3 14.5V4.5Z"
                stroke="white"
                strokeWidth="1.5"
                strokeLinejoin="round"
              />
              <path d="M9 3V13" stroke="white" strokeWidth="1.5" />
              <circle cx="6" cy="7.5" r="1" fill="white" />
              <circle cx="12" cy="7.5" r="1" fill="white" />
            </svg>
          </div>
          <CardTitle>Bio-Rad CS 대응 허브</CardTitle>
          <CardDescription>
            계정 정보를 입력하여 로그인하세요
          </CardDescription>
        </CardHeader>
        <CardContent>
          <LoginForm />
        </CardContent>
      </Card>
    </div>
  );
}
