import Link from "next/link";

interface BreadcrumbItem {
	label: string;
	href?: string;
}

interface BreadcrumbsProps {
	items: BreadcrumbItem[];
}

export function Breadcrumbs({ items }: BreadcrumbsProps) {
	return (
		<nav aria-label="パンくずリスト" className="mb-4">
			<ol className="flex items-center gap-2 text-sm text-gray-500">
				{items.map((item, index) => (
					<li key={item.label} className="flex items-center gap-2">
						{index > 0 && <span aria-hidden="true">/</span>}
						{item.href ? (
							<Link href={item.href} className="hover:text-gray-700 hover:underline">
								{item.label}
							</Link>
						) : (
							<span className="text-gray-900 font-medium">{item.label}</span>
						)}
					</li>
				))}
			</ol>
		</nav>
	);
}
